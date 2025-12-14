package wallet_service.eci.edu.co.controller;

import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;

import wallet_service.eci.edu.co.dto.ProductRequest;
import wallet_service.eci.edu.co.dto.StripeResponse;
import wallet_service.eci.edu.co.service.StripeService;
import wallet_service.eci.edu.co.service.WalletService;

@RestController
@RequestMapping("/api/stripe")
@CrossOrigin(origins = "*")
public class StripeController {

    private static final Logger logger = LoggerFactory.getLogger(StripeController.class);
    private static final String STATUS_SUCCESS = "success";
    private static final String MESSAGE = "message";
    private static final String ERROR = "error";

    private final StripeService stripeService;
    private final WalletService walletService;

    @Value("${stripe.publicKey}")
    private String publicKey;

    @Value("${stripe.secretKey}")
    private String secretKey;

    public StripeController(StripeService stripeService, WalletService walletService) {
        this.stripeService = stripeService;
        this.walletService = walletService;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        logger.info("Stripe apiKey inicializada (secretKey configurada: {})", secretKey != null && !secretKey.isBlank());
    }

    @PostMapping("/checkout")
    public ResponseEntity<StripeResponse> createCheckout(@RequestBody ProductRequest request,
                                                        Authentication authentication) {
        String userId = (authentication != null) ? authentication.getName() : "TEST_USER";
        StripeResponse response = stripeService.createCheckoutSession(request, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/public-key")
    public Map<String, String> getPublicKey() {
        return Map.of("publicKey", publicKey);
    }

    @GetMapping("/success")
    public Map<String, Object> success(@RequestParam(name = "session_id", required = false) String sessionId) {
        return Map.of("status", STATUS_SUCCESS, "sessionId", sessionId);
    }

    @GetMapping("/cancel")
    public Map<String, Object> cancel() {
        return Map.of("status", "canceled", MESSAGE, "Pago cancelado por el usuario");
    }

    @PostMapping("/confirm-payment")
    public ResponseEntity<Object> confirmPayment(@RequestBody Map<String, String> payload) {
        try {
            String rawSessionId = payload.get("sessionId");
            logger.info("Confirmando pago. sessionId raw: {}", rawSessionId);

            if (rawSessionId == null || rawSessionId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(ERROR, "sessionId es requerido"));
            }

            int hashIndex = rawSessionId.indexOf('#');
            if (hashIndex != -1) rawSessionId = rawSessionId.substring(0, hashIndex);

            String sessionId = rawSessionId.trim();
            logger.info("SessionId normalizado: {}", sessionId);


            Session session = Session.retrieve(sessionId);

            logger.info("Session recuperada. Status: {}, Payment Status: {}",
                    session.getStatus(), session.getPaymentStatus());

            if (!"complete".equals(session.getStatus()) || !"paid".equals(session.getPaymentStatus())) {
                return ResponseEntity.badRequest().body(Map.of(ERROR, "El pago no ha sido completado"));
            }

            Map<String, String> metadata = session.getMetadata();
            String userId = metadata.get("userId");
            String tokensStr = metadata.get("tokens");
            String tokenPriceStr = metadata.get("tokenPrice");

            if (userId == null || tokensStr == null || tokenPriceStr == null) {
                return ResponseEntity.badRequest().body(Map.of(ERROR, "Metadata incompleta en la sesión"));
            }

            int tokens = Integer.parseInt(tokensStr);
            double tokenPrice = Double.parseDouble(tokenPriceStr);
            double amount = tokens * tokenPrice;

            walletService.getOrCreateWallet(userId, "");
            walletService.processPurchase(userId, tokens, amount, sessionId);

            return ResponseEntity.ok(Map.of(
                    STATUS_SUCCESS, true,
                    MESSAGE, "Pago procesado exitosamente",
                    "tokens", tokens,
                    "amount", amount
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR, "Error al procesar el pago: " + e.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleStripeWebhook(@RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        logger.info("Webhook recibido de Stripe");
        logger.debug("Payload: {}", payload);
        return ResponseEntity.ok().build();
    }
}
