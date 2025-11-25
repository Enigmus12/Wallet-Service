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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@CrossOrigin(origins = "*")
public class WalletController {
    
    private static final Logger logger = LoggerFactory.getLogger(WalletController.class);
    
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
    public ResponseEntity<?> getStudentWallet(Authentication authentication,
                                             @RequestHeader("Authorization") String token) {
        try {
            String userId = authentication.getName();
            CognitoTokenDecoder.CognitoUserInfo userInfo = tokenDecoder.extractUserInfo(token.replace("Bearer ", ""));
            
            Wallet studentWallet = walletService.getOrCreateWallet(userId, "STUDENT", userInfo.getEmail());
            
            return ResponseEntity.ok(studentWallet);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al obtener wallet de estudiante: " + e.getMessage()));
        }
    }
    
    /**
     * Obtiene la wallet del tutor
     */
    @GetMapping("/tutor")
    public ResponseEntity<?> getTutorWallet(Authentication authentication,
                                           @RequestHeader("Authorization") String token) {
        try {
            String userId = authentication.getName();
            CognitoTokenDecoder.CognitoUserInfo userInfo = tokenDecoder.extractUserInfo(token.replace("Bearer ", ""));
            
            Wallet tutorWallet = walletService.getOrCreateWallet(userId, "TUTOR", userInfo.getEmail());
            
            return ResponseEntity.ok(tutorWallet);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al obtener wallet de tutor: " + e.getMessage()));
        }
    }
    
    /**
     * Obtiene el saldo de tokens del estudiante
     */
    @GetMapping("/balance/student")
    public ResponseEntity<?> getStudentBalance(Authentication authentication) {
        String userId = authentication.getName();
        try {
            Integer balance = walletService.getTokenBalance(userId, "STUDENT");
            return ResponseEntity.ok(Map.of(
                    "userId", userId,
                    "role", "STUDENT",
                    "tokenBalance", balance
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "userId", userId,
                    "role", "STUDENT",
                    "tokenBalance", 0,
                    "warning", "No se pudo obtener la wallet: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Obtiene el saldo de tokens del tutor
     */
    @GetMapping("/balance/tutor")
    public ResponseEntity<?> getTutorBalance(Authentication authentication) {
        String userId = authentication.getName();
        try {
            Integer balance = walletService.getTokenBalance(userId, "TUTOR");
            return ResponseEntity.ok(Map.of(
                    "userId", userId,
                    "role", "TUTOR",
                    "tokenBalance", balance
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "userId", userId,
                    "role", "TUTOR",
                    "tokenBalance", 0,
                    "warning", "No se pudo obtener la wallet: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Transfiere tokens de un estudiante a un tutor cuando se acepta una reservación
     */
    @PostMapping("/transfer")
    public ResponseEntity<?> transferTokens(Authentication authentication,
                                           @RequestBody Map<String, Object> request) {
        try {
            String fromUserId = (String) request.get("fromUserId"); // Estudiante que hace la reserva
            String toUserId = (String) request.get("toUserId"); // Tutor que recibe los tokens
            Integer tokens = (Integer) request.get("tokens");
            String reservationId = (String) request.get("reservationId");
            
            if (fromUserId == null || fromUserId.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "El ID del estudiante es requerido"));
            }
            
            if (toUserId == null || toUserId.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "El ID del tutor es requerido"));
            }
            
            if (tokens == null || tokens <= 0) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "La cantidad de tokens debe ser mayor a 0"));
            }
            
            Map<String, Object> result = walletService.transferTokens(
                fromUserId, toUserId, tokens, 
                "Pago por reservación: " + reservationId
            );
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al transferir tokens: " + e.getMessage()));
        }
    }
    
    /**
     * Maneja cancelaciones tanto por estudiante como por tutor.
     */
    @PostMapping("/refund")
    public ResponseEntity<?> refundOrTransferOnCancellation(Authentication authentication,
                                                            @RequestBody Map<String, Object> request) {
        try {
            String fromUserId = (String) request.get("fromUserId"); // Estudiante
            String toUserId = (String) request.get("toUserId");     // Tutor
            Integer tokens = (Integer) request.get("tokens");
            String reservationId = (String) request.get("reservationId");
            String cancelledBy = (String) request.get("cancelledBy");
            String reason = (String) request.getOrDefault("reason", "Cancelación de reservación");

            if (tokens == null || tokens <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "La cantidad de tokens debe ser mayor a 0"));
            }

            if (cancelledBy == null || cancelledBy.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El campo 'cancelledBy' es requerido (STUDENT o TUTOR)"));
            }

            if (fromUserId == null || fromUserId.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El ID del estudiante ('fromUserId') es requerido"));
            }

            if (toUserId == null || toUserId.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El ID del tutor ('toUserId') es requerido"));
            }

            if ("STUDENT".equalsIgnoreCase(cancelledBy)) {
                // Estudiante cancela: transfiere tokens del estudiante al tutor (penalización)
                Map<String, Object> result = walletService.transferTokens(
                        fromUserId,
                        toUserId,
                        tokens,
                        reason + " - Cancelación por estudiante - Reservación: " + reservationId
                );
                return ResponseEntity.ok(result);
            } else if ("TUTOR".equalsIgnoreCase(cancelledBy)) {
                // Tutor cancela: reembolsa tokens al estudiante Y descuenta del tutor
                Map<String, Object> result = walletService.refundTokens(
                        fromUserId,  // estudiante (recibe)
                        toUserId,    // tutor (pierde)
                        tokens,
                        reason + " - Cancelación por tutor - Reservación: " + reservationId
                );
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Valor inválido para 'cancelledBy'. Use STUDENT o TUTOR"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al procesar cancelación: " + e.getMessage()));
        }
    }
    
    /**
     * Verifica si el estudiante tiene suficientes tokens
     */
    @GetMapping("/student/check/{tokens}")
    public ResponseEntity<?> checkStudentTokens(Authentication authentication,
                                               @PathVariable Integer tokens) {
        try {
            String userId = authentication.getName();
            boolean hasEnough = walletService.hasEnoughTokens(userId, "STUDENT", tokens);
            Integer currentBalance = walletService.getTokenBalance(userId, "STUDENT");
            
            return ResponseEntity.ok(Map.of(
                "hasEnoughTokens", hasEnough,
                "requiredTokens", tokens,
                "currentBalance", currentBalance,
                "role", "STUDENT"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al verificar tokens: " + e.getMessage()));
        }
    }
    
    /**
     * Obtiene el historial de transacciones del estudiante
     */
    @GetMapping("/student/transactions")
    public ResponseEntity<?> getStudentTransactions(Authentication authentication,
                                                   @RequestParam(required = false) String type) {
        try {
            String userId = authentication.getName();
            List<Transaction> transactions;
            
            if (type != null) {
                switch (type.toUpperCase()) {
                    case "PURCHASE":
                        transactions = walletService.getPurchaseHistory(userId, "STUDENT");
                        break;
                    case "USAGE":
                        transactions = walletService.getUsageHistory(userId, "STUDENT");
                        break;
                    default:
                        transactions = walletService.getTransactionHistory(userId, "STUDENT");
                }
            } else {
                transactions = walletService.getTransactionHistory(userId, "STUDENT");
            }
            
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al obtener transacciones: " + e.getMessage()));
        }
    }
    
    /**
     * Obtiene el historial de transacciones del tutor
     */
    @GetMapping("/tutor/transactions")
    public ResponseEntity<?> getTutorTransactions(Authentication authentication,
                                                 @RequestParam(required = false) String type) {
        try {
            String userId = authentication.getName();
            List<Transaction> transactions;
            
            if (type != null) {
                switch (type.toUpperCase()) {
                    case "PURCHASE":
                        transactions = walletService.getPurchaseHistory(userId, "TUTOR");
                        break;
                    case "USAGE":
                        transactions = walletService.getUsageHistory(userId, "TUTOR");
                        break;
                    default:
                        transactions = walletService.getTransactionHistory(userId, "TUTOR");
                }
            } else {
                transactions = walletService.getTransactionHistory(userId, "TUTOR");
            }
            
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al obtener transacciones: " + e.getMessage()));
        }
    }
    
    /**
     * Obtiene transacciones en un rango de fechas del estudiante
     */
    @GetMapping("/student/transactions/range")
    public ResponseEntity<?> getStudentTransactionsByRange(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        try {
            String userId = authentication.getName();
            List<Transaction> transactions = walletService.getTransactionsByDateRange(userId, "STUDENT", startDate, endDate);
            
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al obtener transacciones: " + e.getMessage()));
        }
    }
    
    /**
     * Obtiene transacciones en un rango de fechas del tutor
     */
    @GetMapping("/tutor/transactions/range")
    public ResponseEntity<?> getTutorTransactionsByRange(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        try {
            String userId = authentication.getName();
            List<Transaction> transactions = walletService.getTransactionsByDateRange(userId, "TUTOR", startDate, endDate);
            
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al obtener transacciones: " + e.getMessage()));
        }
    }
    
    /**
     * Webhook para procesar pagos exitosos de Stripe (llamado desde el StripeService)
     * Los tokens comprados se agregan a la wallet del ESTUDIANTE
     */
    @PostMapping("/process-purchase")
    public ResponseEntity<?> processPurchase(@RequestBody Map<String, Object> purchaseData) {
        try {
            String userId = (String) purchaseData.get("userId");
            Integer tokens = (Integer) purchaseData.get("tokens");
            Double amount = ((Number) purchaseData.get("amount")).doubleValue();
            String stripeSessionId = (String) purchaseData.get("stripeSessionId");
            
            // Las compras siempre van a la wallet del estudiante
            Transaction transaction = walletService.processPurchase(userId, "STUDENT", tokens, amount, stripeSessionId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Compra procesada exitosamente",
                "transaction", transaction,
                "role", "STUDENT"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al procesar compra: " + e.getMessage()));
        }
    }
}

