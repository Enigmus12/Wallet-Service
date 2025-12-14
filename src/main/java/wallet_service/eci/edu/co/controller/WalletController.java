package wallet_service.eci.edu.co.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import wallet_service.eci.edu.co.model.Transaction;
import wallet_service.eci.edu.co.model.Wallet;
import wallet_service.eci.edu.co.service.WalletService;
import wallet_service.eci.edu.co.util.CognitoTokenDecoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@CrossOrigin(origins = "*")
public class WalletController {

    private static final String STUDENT_ROLE = "STUDENT";
    private static final String TUTOR_ROLE = "TUTOR";
    private static final String USER_ID_KEY = "userId";
    private static final String ERROR_KEY = "error";
    private static final String TOKEN_BALANCE_KEY = "tokenBalance";

    private final WalletService walletService;
    private final CognitoTokenDecoder tokenDecoder;

    @Autowired
    public WalletController(WalletService walletService, CognitoTokenDecoder tokenDecoder) {
        this.walletService = walletService;
        this.tokenDecoder = tokenDecoder;
    }

    /**
     * Obtiene la wallet del estudiante
     */
    @GetMapping("/student")
    public ResponseEntity<Wallet> getStudentWallet(Authentication authentication,
            @RequestHeader("Authorization") String token) {
        try {
            String userId = authentication.getName();
            CognitoTokenDecoder.CognitoUserInfo userInfo = tokenDecoder.extractUserInfo(token.replace("Bearer ", ""));

            Wallet studentWallet = walletService.getOrCreateWallet(userId, STUDENT_ROLE, userInfo.getEmail());

            return ResponseEntity.ok(studentWallet);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    /**
     * Obtiene la wallet del tutor
     */
    @GetMapping("/tutor")
    public ResponseEntity<Wallet> getTutorWallet(Authentication authentication,
            @RequestHeader("Authorization") String token) {
        try {
            String userId = authentication.getName();
            CognitoTokenDecoder.CognitoUserInfo userInfo = tokenDecoder.extractUserInfo(token.replace("Bearer ", ""));
            Wallet tutorWallet = walletService.getOrCreateWallet(userId, TUTOR_ROLE, userInfo.getEmail());

            return ResponseEntity.ok(tutorWallet);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    /**
     * Obtiene el saldo de tokens del estudiante
     */
    @GetMapping("/balance/student")
    public ResponseEntity<Map<String, Object>> getStudentBalance(Authentication authentication) {
        try {
            String userId = authentication.getName();
            Integer balance = walletService.getTokenBalance(userId, STUDENT_ROLE);
            return ResponseEntity.ok(Map.of(
                    USER_ID_KEY, userId,
                    "role", STUDENT_ROLE,
                    TOKEN_BALANCE_KEY, balance));
        } catch (Exception e) {
            String userId = authentication.getName();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    USER_ID_KEY, userId,
                    "role", STUDENT_ROLE,
                    TOKEN_BALANCE_KEY, 0,
                    "warning", "No se pudo obtener la wallet: " + e.getMessage()));
        }
    }

    /**
     * Obtiene el saldo de tokens del tutor
     */
    @GetMapping("/balance/tutor")
    public ResponseEntity<Map<String, Object>> getTutorBalance(Authentication authentication) {
        try {
            String userId = authentication.getName();
            Integer balance = walletService.getTokenBalance(userId, TUTOR_ROLE);
            return ResponseEntity.ok(Map.of(
                    USER_ID_KEY, userId,
                    "role", TUTOR_ROLE,
                    TOKEN_BALANCE_KEY, balance));
        } catch (Exception e) {
            String userId = authentication.getName();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    USER_ID_KEY, userId,
                    "role", TUTOR_ROLE,
                    TOKEN_BALANCE_KEY, 0,
                    "warning", "No se pudo obtener la wallet: " + e.getMessage()));
        }
    }

    /**
     * Transfiere tokens de un estudiante a un tutor cuando se acepta una
     * reservación
     */
    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transferTokens(Authentication authentication,
            @RequestBody Map<String, Object> request) {
        try {
            String fromUserId = (String) request.get("fromUserId"); // Estudiante que hace la reserva
            String toUserId = (String) request.get("toUserId"); // Tutor que recibe los tokens
            String reservationId = (String) request.get("reservationId");
            Integer tokens = (Integer) request.get("tokens");

            if (fromUserId == null || fromUserId.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(ERROR_KEY, "El ID del estudiante es requerido"));
            }

            if (toUserId == null || toUserId.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(ERROR_KEY, "El ID del tutor es requerido"));
            }

            if (tokens == null || tokens <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of(ERROR_KEY, "La cantidad de tokens debe ser mayor a 0"));
            }

            Map<String, Object> result = walletService.transferTokens(
                    fromUserId,
                    toUserId,
                    tokens,
                    "Pago por reservación: " + reservationId,
                    reservationId);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Error al transferir tokens: " + e.getMessage()));
        }
    }

    /**
     * Maneja cancelaciones tanto por estudiante como por tutor.
     */
    @PostMapping("/refund")
    public ResponseEntity<Map<String, Object>> refundOnCancellation(Authentication authentication,
            @RequestBody Map<String, Object> request) {
        try {
            String fromUserId = (String) request.get("fromUserId"); // Estudiante
            String toUserId = (String) request.get("toUserId"); // Tutor
            String reservationId = (String) request.get("reservationId");
            String cancelledBy = (String) request.get("cancelledBy");
            String reason = (String) request.getOrDefault("reason", "Cancelación de reservación");
            if (reservationId == null || reservationId.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(ERROR_KEY, "El 'reservationId' es requerido"));
            }

            if (cancelledBy == null || cancelledBy.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(ERROR_KEY, "El campo 'cancelledBy' es requerido (STUDENT o TUTOR)"));
            }

            if (fromUserId == null || fromUserId.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(ERROR_KEY, "El ID del estudiante ('fromUserId') es requerido"));
            }

            if (toUserId == null || toUserId.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(ERROR_KEY, "El ID del tutor ('toUserId') es requerido"));
            }
            if (STUDENT_ROLE.equalsIgnoreCase(cancelledBy) || TUTOR_ROLE.equalsIgnoreCase(cancelledBy)) {
                
                Map<String, Object> result = walletService.refundTokensByBooking(
                        fromUserId,
                        toUserId,
                        reservationId,
                        reason + " - Cancelación por " + cancelledBy + " - Reservación: " + reservationId);
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of(ERROR_KEY, "Valor inválido para 'cancelledBy'. Use STUDENT o TUTOR"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Error al procesar cancelación: " + e.getMessage()));
        }
    }

    /**
     * Verifica si el estudiante tiene suficientes tokens
     */
    @GetMapping("/student/check/{tokens}")
    public ResponseEntity<Map<String, Object>> checkStudentTokens(Authentication authentication,
            @PathVariable Integer tokens) {
        try {
            String userId = authentication.getName();
            boolean hasEnough = walletService.hasEnoughTokens(userId, STUDENT_ROLE, tokens);
            Integer currentBalance = walletService.getTokenBalance(userId, STUDENT_ROLE);

            return ResponseEntity.ok(Map.of(
                    "hasEnoughTokens", hasEnough,
                    "requiredTokens", tokens,
                    "currentBalance", currentBalance,
                    "role", STUDENT_ROLE));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Error al verificar tokens: " + e.getMessage()));
        }
    }

    /**
     * Obtiene el historial de transacciones del estudiante
     */
    @GetMapping("/student/transactions")
    public ResponseEntity<List<Transaction>> getStudentTransactions(Authentication authentication,
            @RequestParam(required = false) String type) {
        try {
            String userId = authentication.getName();
            List<Transaction> transactions;

            if (type != null) {
                switch (type.toUpperCase()) {
                    case "PURCHASE":
                        transactions = walletService.getPurchaseHistory(userId, STUDENT_ROLE);
                        break;
                    case "USAGE":
                        transactions = walletService.getUsageHistory(userId, STUDENT_ROLE);
                        break;
                    default:
                        transactions = walletService.getTransactionHistory(userId, STUDENT_ROLE);
                }
            } else {
                transactions = walletService.getTransactionHistory(userId, STUDENT_ROLE);
            }

            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    /**
     * Obtiene transacciones en un rango de fechas del estudiante
     */
    @GetMapping("/student/transactions/range")
    public ResponseEntity<List<Transaction>> getStudentTransactionsByRange(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        try {
            String userId = authentication.getName();
            List<Transaction> transactions = walletService.getTransactionsByDateRange(userId, STUDENT_ROLE, startDate,
                    endDate);

            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    /**
     * Obtiene transacciones en un rango de fechas del tutor
     */
    @GetMapping("/tutor/transactions/range")
    public ResponseEntity<List<Transaction>> getTutorTransactionsByRange(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        try {
            String userId = authentication.getName();
            List<Transaction> transactions = walletService.getTransactionsByDateRange(userId, TUTOR_ROLE, startDate,
                    endDate);

            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    /**
     * Webhook para procesar pagos exitosos de Stripe (llamado desde el
     * StripeService)
     * Los tokens comprados se agregan a la wallet del ESTUDIANTE
     */
    @PostMapping("/process-purchase")
    public ResponseEntity<Map<String, Object>> processPurchase(@RequestBody Map<String, Object> purchaseData) {
        try {
            String userId = (String) purchaseData.get(USER_ID_KEY);
            Integer tokens = (Integer) purchaseData.get("tokens");
            Double amount = ((Number) purchaseData.get("amount")).doubleValue();
            String stripeSessionId = (String) purchaseData.get("stripeSessionId");

            // Las compras siempre van a la wallet del estudiante
            Transaction transaction = walletService.processPurchase(userId, STUDENT_ROLE, tokens, amount,
                    stripeSessionId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Compra procesada exitosamente",
                    "transaction", transaction,
                    "role", STUDENT_ROLE));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Error al procesar compra: " + e.getMessage()));
        }
    }
}