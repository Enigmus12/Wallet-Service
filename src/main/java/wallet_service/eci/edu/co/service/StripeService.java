package wallet_service.eci.edu.co.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

import wallet_service.eci.edu.co.dto.ProductRequest;
import wallet_service.eci.edu.co.dto.StripeResponse;

@Service // ✅ clave: ahora Spring lo detecta y lo puede inyectar en StripeController
public class StripeService {

    private static final Logger logger = LoggerFactory.getLogger(StripeService.class);
    private static final String CHECKOUT_SESSION_ID_PLACEHOLDER = "{CHECKOUT_SESSION_ID}";
    private final String successUrl;
    private final String cancelUrl;

    public StripeService(
            @Value("${stripe.secretKey}") String secretKey,
            @Value("${stripe.successUrl}") String successUrl,
            @Value("${stripe.cancelUrl}") String cancelUrl) {
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
        Stripe.apiKey = secretKey;
    }
    public StripeResponse createCheckoutSession(ProductRequest request, String userId) {

        try {
            String finalSuccessUrl = buildFinalSuccessUrl(successUrl);

            long quantity = (request.getQuantity() == null) ? 1L : request.getQuantity();
            String currency = (request.getCurrency() == null) ? "cop" : request.getCurrency().toLowerCase();
            String name = (request.getName() == null) ? "Token" : request.getName();

            // ✅ Si quieres 2000 COP por token, en COP el unitAmount suele ser en COP (sin centavos)
            long unitAmount = 2000L;

            Map<String, String> metadata = new HashMap<>();
            metadata.put("userId", userId);
            metadata.put("tokens", String.valueOf(quantity));
            metadata.put("tokenPrice", "2000");

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(finalSuccessUrl)
                    .setCancelUrl(cancelUrl)
                    .putAllMetadata(metadata)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(quantity)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(currency)
                                    .setUnitAmount(unitAmount)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(name)
                                            .setDescription("Tokens para usar en la plataforma")
                                            .build())
                                    .build())
                            .build())
                    .build();

            Session session = Session.create(params);

            logger.info("✅ Checkout Session creada: ID={}", session.getId());
            logger.info("➡️  URL de pago Stripe: {}", session.getUrl());
            if (logger.isInfoEnabled()) {
                logger.info("🔁 Redirige a: {}", finalSuccessUrl.replace(CHECKOUT_SESSION_ID_PLACEHOLDER, session.getId()));
            }

            return new StripeResponse("success", "Checkout session created", session.getId(), session.getUrl());
        } catch (StripeException e) {
            logger.error("❌ Error creando checkout session: {}", e.getMessage(), e);
            return new StripeResponse("error", e.getMessage(), null, null);
        }
    }

    private static String buildFinalSuccessUrl(String successUrl) {
        String urlSeparator = successUrl.contains("?") ? "&" : "?";
        if (successUrl.contains(CHECKOUT_SESSION_ID_PLACEHOLDER)) return successUrl;
        return successUrl + urlSeparator + "session_id=" + CHECKOUT_SESSION_ID_PLACEHOLDER;
    }
}
