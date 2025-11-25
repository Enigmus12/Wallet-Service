package wallet_service.eci.edu.co.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

import wallet_service.eci.edu.co.dto.ProductRequest;
import wallet_service.eci.edu.co.dto.StripeResponse;

import java.util.HashMap;
import java.util.Map;

@Service
public class StripeService {

    @Value("${stripe.secretKey}")
    private String secretKey;

    @Value("${stripe.successUrl}")
    private String successUrl;

    @Value("${stripe.cancelUrl}")
    private String cancelUrl;

    public StripeResponse createCheckoutSession(ProductRequest request, String userId) {
        Stripe.apiKey = secretKey;

        // Normalizar success URL: si ya contiene el placeholder, usarla tal cual,
        // si no, agregar el query param con el session id
        String finalSuccessUrl = successUrl.contains("{CHECKOUT_SESSION_ID}")
            ? successUrl
            : successUrl + (successUrl.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}";

        // Debug: mostrar URLs configuradas
        System.out.println("🔍 SUCCESS URL (config): " + successUrl);
        System.out.println("🔍 SUCCESS URL (final usada en Stripe): " + finalSuccessUrl);
        System.out.println("🔍 CANCEL URL:  " + cancelUrl);
        System.out.println("🔍 Usuario (metadata userId): " + userId);

        long quantity = request.getQuantity() == null ? 1L : request.getQuantity();
        String currency = request.getCurrency() == null ? "cop" : request.getCurrency().toLowerCase();
        String name = request.getName() == null ? "Token" : request.getName();

        // Cada token cuesta 5000 pesos colombianos
        // Nota: Aunque COP no tiene centavos, Stripe maneja todos los montos como si tuvieran
        // Por eso multiplicamos por 100: 2000 * 100 = 200000 para que Stripe lo interprete como 2000 COP
        long unitAmount = 200000L; // 2000 COP por token

        // Crear metadata con información del usuario y tokens
        Map<String, String> metadata = new HashMap<>();
        metadata.put("userId", userId);
        metadata.put("tokens", String.valueOf(quantity));
        metadata.put("tokenPrice", "2000");

        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl(finalSuccessUrl)
            .setCancelUrl(cancelUrl)
                .putAllMetadata(metadata) // Agregar metadata
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

        try {
            Session session = Session.create(params);
            System.out.println("✅ Checkout Session creada: ID=" + session.getId());
            System.out.println("➡️  URL de pago Stripe (abrir en navegador): " + session.getUrl());
            System.out.println("🔁 Redirige a (cuando COMPLETE/PAGADO): " + finalSuccessUrl.replace("{CHECKOUT_SESSION_ID}", session.getId()));
            return new StripeResponse("success", "Checkout session created", session.getId(), session.getUrl());
        } catch (StripeException e) {
            return new StripeResponse("error", e.getMessage(), null, null);
        }
    }
}
