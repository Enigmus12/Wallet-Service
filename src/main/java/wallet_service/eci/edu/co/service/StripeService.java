package wallet_service.eci.edu.co.service;

import org.springframework.beans.factory.annotation.Value;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

import wallet_service.eci.edu.co.dto.ProductRequest;
import wallet_service.eci.edu.co.dto.StripeResponse;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StripeService {

    private static final Logger logger = LoggerFactory.getLogger(StripeService.class);
    private static final String CHECKOUT_SESSION_ID_PLACEHOLDER = "{CHECKOUT_SESSION_ID}";
    private static final String USER_ID_LOG_MESSAGE = "🔍 Usuario (metadata userId): {}";
    private static final String REDIRECT_LOG_MESSAGE = "🔁 Redirige a (cuando COMPLETE/PAGADO): {}";

    @Value("${stripe.secretKey}")
    private String secretKey;

    @Value("${stripe.successUrl}")
    private String successUrl;

    @Value("${stripe.cancelUrl}")
    private String cancelUrl;

    public static StripeResponse createCheckoutSession(ProductRequest request, String userId, String secretKey,
            String successUrl, String cancelUrl) {
        Stripe.apiKey = secretKey;

        try {
            String finalSuccessUrl = buildFinalSuccessUrl(successUrl);
            logConfiguredUrls(successUrl, finalSuccessUrl, cancelUrl, userId);

            long quantity = request.getQuantity() == null ? 1L : request.getQuantity();
            String currency = request.getCurrency() == null ? "cop" : request.getCurrency().toLowerCase();
            String name = request.getName() == null ? "Token" : request.getName();

            long unitAmount = 200000L; // 2000 COP por token

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
            logRedirectUrl(finalSuccessUrl, session.getId());
            logger.info("✅ Checkout Session creada: ID={}", session.getId());
            logger.info("➡️  URL de pago Stripe (abrir en navegador): {}", session.getUrl());
            logRedirectUrl(finalSuccessUrl, session.getId());
            return new StripeResponse("success", "Checkout session created", session.getId(), session.getUrl());
        } catch (StripeException e) {
            return new StripeResponse("error", e.getMessage(), null, null);
        }
    }

    private static String buildFinalSuccessUrl(String successUrl) {
        String urlSeparator = successUrl.contains("?") ? "&" : "?";
        if (successUrl.contains(CHECKOUT_SESSION_ID_PLACEHOLDER)) {
            return successUrl;
        } else {
            return successUrl + urlSeparator + "session_id=" + CHECKOUT_SESSION_ID_PLACEHOLDER;
        }
    }

    private static void logConfiguredUrls(String successUrl, String finalSuccessUrl, String cancelUrl, String userId) {
        logger.info("🔍 SUCCESS URL (config): {}", successUrl);
        logger.info("🔍 SUCCESS URL (final usada en Stripe): {}", finalSuccessUrl);
        logger.info(USER_ID_LOG_MESSAGE, userId);
        logger.info("🔍 CANCEL URL:  {}", cancelUrl);
    }

    private static void logRedirectUrl(String finalSuccessUrl, String sessionId) {
        if (finalSuccessUrl != null) {
            if (finalSuccessUrl.contains(CHECKOUT_SESSION_ID_PLACEHOLDER)) {
                if (sessionId != null) {
                    if (logger.isInfoEnabled()) {
                        logger.info(REDIRECT_LOG_MESSAGE,
                                finalSuccessUrl.replace(CHECKOUT_SESSION_ID_PLACEHOLDER, sessionId));
                    }
                } else {
                    logger.info(REDIRECT_LOG_MESSAGE, finalSuccessUrl);
                }
            } else {
                logger.info(REDIRECT_LOG_MESSAGE, finalSuccessUrl);
            }
        } else {
            logger.info(REDIRECT_LOG_MESSAGE, "Final success URL is null");
        }
    }

    public StripeResponse createCheckoutSession(ProductRequest request, String userId) {
        return createCheckoutSession(request, userId, secretKey, successUrl, cancelUrl);
    }
}
