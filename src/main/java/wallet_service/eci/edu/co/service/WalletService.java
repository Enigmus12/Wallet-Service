package wallet_service.eci.edu.co.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import wallet_service.eci.edu.co.model.Transaction;
import wallet_service.eci.edu.co.model.Wallet;
import wallet_service.eci.edu.co.repository.TransactionRepository;
import wallet_service.eci.edu.co.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WalletService {
    
    private static final Logger logger = LoggerFactory.getLogger(WalletService.class);
    
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    
    // Mapa de locks para sincronizar creación de wallets por userId
    private final java.util.concurrent.ConcurrentHashMap<String, Object> walletLocks = new java.util.concurrent.ConcurrentHashMap<>();
    
    @Autowired
    public WalletService(WalletRepository walletRepository, 
                        TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }
    
    /**
     * Obtiene o crea una wallet para un usuario con rol específico (thread-safe)
     * @param actualUserId Sub de Cognito (sin sufijo de rol)
     * @param role Rol del usuario ("STUDENT" o "TUTOR")
     * @param email Email del usuario
     * @return Wallet del usuario con el rol especificado
     */
    public Wallet getOrCreateWallet(String actualUserId, String role, String email) {
        String walletUserId = actualUserId + "-" + role.toLowerCase();
        logger.info("getOrCreateWallet - actualUserId: {}, role: {}, walletUserId: {}, email: {}", 
                   actualUserId, role, walletUserId, email);
        
        // Obtener o crear un lock específico para este walletUserId (evita condiciones de carrera)
        Object lock = walletLocks.computeIfAbsent(walletUserId, k -> new Object());
        
        synchronized (lock) {
            try {
                // Buscar wallet existente dentro del bloque sincronizado
                var all = walletRepository.findAllByUserId(walletUserId);
                
                if (!all.isEmpty()) {
                    if (all.size() > 1) {
                        logger.warn("Se detectaron {} wallets duplicadas para userId {}. Procediendo a limpiar...", all.size(), walletUserId);
                        // Conservar la primera y eliminar el resto
                        Wallet primary = all.get(0);
                        for (int i = 1; i < all.size(); i++) {
                            try {
                                walletRepository.delete(all.get(i));
                            } catch (Exception e) {
                                logger.error("Error eliminando duplicado: {}", e.getMessage());
                            }
                        }
                        logger.info("Duplicados eliminados. Wallet primaria id: {}", primary.getId());
                        return primary;
                    } else {
                        logger.info("Wallet existente encontrada para userId: {}", walletUserId);
                        return all.get(0);
                    }
                }
                
                // Crear nueva wallet con rol
                Wallet newWallet = new Wallet(actualUserId, role, email);
                logger.info("Creando nueva wallet para userId: {} con rol: {}", walletUserId, role);
                Wallet savedWallet = walletRepository.save(newWallet);
                logger.info("Wallet guardada exitosamente con id: {}", savedWallet.getId());
                return savedWallet;
                
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // Si aún así se genera un duplicado (por índice único), reintentamos buscar
                logger.warn("DuplicateKeyException detectada para userId: {}. Reintentando búsqueda...", walletUserId);
                return walletRepository.findAllByUserId(walletUserId).stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No se pudo obtener wallet después de DuplicateKeyException"));
            } finally {
                // Limpiar el lock después de usarlo para evitar memory leaks
                walletLocks.remove(walletUserId);
            }
        }
    }
    
    /**
     * Obtiene o crea una wallet (legacy, sin rol especificado - usa STUDENT por defecto)
     * @param userId Sub de Cognito
     * @param email Email del usuario
     * @return Wallet del usuario
     */
    public Wallet getOrCreateWallet(String userId, String email) {
        return getOrCreateWallet(userId, "STUDENT", email);
    }
    
    /**
     * Obtiene la wallet de un usuario con rol específico
     * @param actualUserId Sub de Cognito (sin sufijo de rol)
     * @param role Rol del usuario ("STUDENT" o "TUTOR")
     * @return Optional con la wallet si existe
     */
    public Optional<Wallet> getWallet(String actualUserId, String role) {
        String walletUserId = actualUserId + "-" + role.toLowerCase();
        return walletRepository.findByUserId(walletUserId);
    }
    
    /**
     * Obtiene la wallet de un usuario (legacy)
     * @param userId Sub de Cognito
     * @return Optional con la wallet si existe
     */
    public Optional<Wallet> getWallet(String userId) {
        return walletRepository.findByUserId(userId);
    }
    
    /**
     * Procesa una compra exitosa de tokens con rol específico (idempotente por stripeSessionId)
     * @param actualUserId Sub de Cognito (sin sufijo de rol)
     * @param role Rol del usuario ("STUDENT" o "TUTOR")
     * @param tokens Cantidad de tokens comprados
     * @param amount Monto pagado en pesos
     * @param stripeSessionId ID de sesión de Stripe
     * @return Transacción creada o existente si ya fue procesada
     */
    public Transaction processPurchase(String actualUserId, String role, Integer tokens, Double amount, String stripeSessionId) {
        String walletUserId = actualUserId + "-" + role.toLowerCase();
        logger.info("processPurchase - actualUserId: {}, role: {}, walletUserId: {}, tokens: {}, amount: {}, stripeSessionId: {}", 
                   actualUserId, role, walletUserId, tokens, amount, stripeSessionId);
        
        // IDEMPOTENCIA: Verificar si ya procesamos esta sesión de Stripe
        Optional<Transaction> existingTransaction = transactionRepository.findByStripeSessionId(stripeSessionId);
        if (existingTransaction.isPresent()) {
            logger.warn("Compra duplicada detectada para stripeSessionId: {}. Retornando transacción existente.", stripeSessionId);
            return existingTransaction.get();
        }
        
        // Obtener la wallet
        Wallet wallet = walletRepository.findByUserId(walletUserId)
            .orElseThrow(() -> new RuntimeException("Wallet no encontrada para el usuario: " + walletUserId));
        
        logger.info("Wallet encontrada con id: {}, balance actual: {}", wallet.getId(), wallet.getTokenBalance());
        
        // Crear transacción de compra
        Transaction transaction = Transaction.createPurchase(walletUserId, wallet.getId(), tokens, amount, stripeSessionId);
        transaction.complete();
        
        logger.info("Transacción creada, guardando...");
        Transaction savedTransaction = transactionRepository.save(transaction);
        logger.info("Transacción guardada con id: {}", savedTransaction.getId());
        
        // Actualizar wallet
        wallet.addTokens(tokens);
        wallet.addToTotalSpent(amount);
        
        logger.info("Actualizando wallet, nuevo balance: {}", wallet.getTokenBalance());
        walletRepository.save(wallet);
        logger.info("Wallet actualizada exitosamente");
        
        return savedTransaction;
    }
    
    /**
     * Procesa una compra exitosa de tokens (legacy - asume STUDENT)
     * @param userId Sub de Cognito
     * @param tokens Cantidad de tokens comprados
     * @param amount Monto pagado en pesos
     * @param stripeSessionId ID de sesión de Stripe
     * @return Transacción creada
     */
    public Transaction processPurchase(String userId, Integer tokens, Double amount, String stripeSessionId) {
        return processPurchase(userId, "STUDENT", tokens, amount, stripeSessionId);
    }
    
    /**
     * Usa tokens de la wallet (legacy)
     * @param userId Sub de Cognito
     * @param tokens Cantidad de tokens a usar
     * @param description Descripción del uso
     * @return true si se usaron exitosamente, false si no hay suficientes tokens
     */
    public boolean useTokens(String userId, Integer tokens, String description) {
        logger.info("useTokens - userId: {}, tokens: {}, description: {}", userId, tokens, description);
        
        // Obtener la wallet
        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("Wallet no encontrada para el usuario: " + userId));
        
        logger.info("Wallet encontrada, balance actual: {}", wallet.getTokenBalance());
        
        // Verificar y usar tokens
        if (wallet.useTokens(tokens)) {
            // Crear transacción de uso
            Transaction transaction = Transaction.createUsage(userId, wallet.getId(), tokens, description);
            
            logger.info("Guardando transacción de uso...");
            transactionRepository.save(transaction);
            
            logger.info("Actualizando wallet, nuevo balance: {}", wallet.getTokenBalance());
            walletRepository.save(wallet);
            
            logger.info("Tokens usados exitosamente");
            return true;
        }
        
        logger.warn("Tokens insuficientes. Requeridos: {}, Disponibles: {}", tokens, wallet.getTokenBalance());
        return false;
    }
    
    /**
     * Verifica si un usuario tiene suficientes tokens con rol específico
     * @param actualUserId Sub de Cognito (sin sufijo de rol)
     * @param role Rol del usuario ("STUDENT" o "TUTOR")
     * @param requiredTokens Cantidad de tokens requeridos
     * @return true si tiene suficientes tokens, false en caso contrario
     */
    public boolean hasEnoughTokens(String actualUserId, String role, Integer requiredTokens) {
        String walletUserId = actualUserId + "-" + role.toLowerCase();
        Optional<Wallet> wallet = walletRepository.findByUserId(walletUserId);
        return wallet.isPresent() && wallet.get().getTokenBalance() >= requiredTokens;
    }
    
    /**
     * Verifica si un usuario tiene suficientes tokens (legacy)
     * @param userId Sub de Cognito
     * @param requiredTokens Cantidad de tokens requeridos
     * @return true si tiene suficientes tokens, false en caso contrario
     */
    public boolean hasEnoughTokens(String userId, Integer requiredTokens) {
        Optional<Wallet> wallet = walletRepository.findByUserId(userId);
        return wallet.isPresent() && wallet.get().getTokenBalance() >= requiredTokens;
    }
    
    /**
     * Obtiene el saldo de tokens de un usuario con rol específico
     * @param actualUserId Sub de Cognito (sin sufijo de rol)
     * @param role Rol del usuario ("STUDENT" o "TUTOR")
     * @return Saldo de tokens
     */
    public Integer getTokenBalance(String actualUserId, String role) {
        String walletUserId = actualUserId + "-" + role.toLowerCase();
        return walletRepository.findByUserId(walletUserId)
            .map(Wallet::getTokenBalance)
            .orElse(0);
    }
    
    /**
     * Obtiene el saldo de tokens de un usuario (legacy)
     * @param userId Sub de Cognito
     * @return Saldo de tokens
     */
    public Integer getTokenBalance(String userId) {
        return walletRepository.findByUserId(userId)
            .map(Wallet::getTokenBalance)
            .orElse(0);
    }
    
    /**
     * Obtiene el historial de transacciones de un usuario con rol específico
     * @param actualUserId Sub de Cognito (sin sufijo de rol)
     * @param role Rol del usuario ("STUDENT" o "TUTOR")
     * @return Lista de transacciones
     */
    public List<Transaction> getTransactionHistory(String actualUserId, String role) {
        String walletUserId = actualUserId + "-" + role.toLowerCase();
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(walletUserId);
    }
    
    /**
     * Obtiene el historial de transacciones de un usuario (legacy)
     * @param userId Sub de Cognito
     * @return Lista de transacciones
     */
    public List<Transaction> getTransactionHistory(String userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * Obtiene las transacciones de compra de un usuario con rol específico
     * @param actualUserId Sub de Cognito (sin sufijo de rol)
     * @param role Rol del usuario ("STUDENT" o "TUTOR")
     * @return Lista de transacciones de compra
     */
    public List<Transaction> getPurchaseHistory(String actualUserId, String role) {
        String walletUserId = actualUserId + "-" + role.toLowerCase();
        return transactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
            walletUserId, Transaction.TransactionType.PURCHASE);
    }
    
    /**
     * Obtiene las transacciones de compra de un usuario (legacy)
     * @param userId Sub de Cognito
     * @return Lista de transacciones de compra
     */
    public List<Transaction> getPurchaseHistory(String userId) {
        return transactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
            userId, Transaction.TransactionType.PURCHASE);
    }
    
    /**
     * Obtiene las transacciones de uso de un usuario con rol específico
     * @param actualUserId Sub de Cognito (sin sufijo de rol)
     * @param role Rol del usuario ("STUDENT" o "TUTOR")
     * @return Lista de transacciones de uso
     */
    public List<Transaction> getUsageHistory(String actualUserId, String role) {
        String walletUserId = actualUserId + "-" + role.toLowerCase();
        return transactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
            walletUserId, Transaction.TransactionType.USAGE);
    }
    
    /**
     * Obtiene las transacciones de uso de un usuario (legacy)
     * @param userId Sub de Cognito
     * @return Lista de transacciones de uso
     */
    public List<Transaction> getUsageHistory(String userId) {
        return transactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
            userId, Transaction.TransactionType.USAGE);
    }
    
    /**
     * Obtiene transacciones en un rango de fechas con rol específico
     * @param actualUserId Sub de Cognito (sin sufijo de rol)
     * @param role Rol del usuario ("STUDENT" o "TUTOR")
     * @param startDate Fecha de inicio
     * @param endDate Fecha de fin
     * @return Lista de transacciones en el rango
     */
    public List<Transaction> getTransactionsByDateRange(String actualUserId, String role, LocalDateTime startDate, LocalDateTime endDate) {
        String walletUserId = actualUserId + "-" + role.toLowerCase();
        return transactionRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            walletUserId, startDate, endDate);
    }
    
    /**
     * Obtiene transacciones en un rango de fechas (legacy)
     * @param userId Sub de Cognito
     * @param startDate Fecha de inicio
     * @param endDate Fecha de fin
     * @return Lista de transacciones en el rango
     */
    public List<Transaction> getTransactionsByDateRange(String userId, LocalDateTime startDate, LocalDateTime endDate) {
        return transactionRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            userId, startDate, endDate);
    }
    
    /**
     * Busca una transacción por el sessionId de Stripe
     * @param stripeSessionId ID de sesión de Stripe
     * @return Optional con la transacción si existe
     */
    public Optional<Transaction> findTransactionByStripeSession(String stripeSessionId) {
        return transactionRepository.findByStripeSessionId(stripeSessionId);
    }
    
    /**
     * Transfiere tokens de un estudiante a un tutor (cuando se acepta la reservación)
     * @param fromUserId Sub de Cognito del estudiante (sin sufijo)
     * @param toUserId Sub de Cognito del tutor (sin sufijo)
     * @param tokens Cantidad de tokens a transferir
     * @param description Descripción de la transferencia
     * @return Map con el resultado de la operación
     */
    public Map<String, Object> transferTokens(String fromUserId, String toUserId, Integer tokens, String description) {
        logger.info("transferTokens - from: {}, to: {}, tokens: {}", fromUserId, toUserId, tokens);
        
        // Construir IDs de wallet con roles
        String studentWalletUserId = fromUserId + "-student";
        String tutorWalletUserId = toUserId + "-tutor";
        
        // Obtener wallet del estudiante
        Wallet studentWallet = walletRepository.findByUserId(studentWalletUserId)
            .orElseThrow(() -> new RuntimeException("Wallet del estudiante no encontrada: " + studentWalletUserId));
        
        // Verificar que el estudiante tenga suficientes tokens
        if (!studentWallet.useTokens(tokens)) {
            throw new RuntimeException("El estudiante no tiene suficientes tokens");
        }
        
        // Obtener o crear wallet del tutor
        Wallet tutorWallet = walletRepository.findByUserId(tutorWalletUserId)
            .orElseGet(() -> {
                // Si el tutor no tiene wallet, crear una
                Wallet newWallet = new Wallet(toUserId, "TUTOR", "");
                return newWallet;
            });
        
        // Agregar tokens al tutor
        tutorWallet.addTokens(tokens);
        
        // Crear transacción de uso para el estudiante (egreso)
        Transaction studentTransaction = Transaction.createUsage(
            studentWalletUserId, studentWallet.getId(), tokens, 
            "Pago a tutor - " + description
        );
        
        // Crear transacción de ingreso para el tutor
        Transaction tutorTransaction = new Transaction();
        tutorTransaction.setUserId(tutorWalletUserId);
        tutorTransaction.setWalletId(tutorWallet.getId());
        tutorTransaction.setType(Transaction.TransactionType.USAGE);
        tutorTransaction.setTokensAmount(tokens);
        tutorTransaction.setMoneyAmount(0.0);
        tutorTransaction.setDescription("Ingreso por tutoría - " + description);
        tutorTransaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        tutorTransaction.setCreatedAt(LocalDateTime.now());
        tutorTransaction.setCompletedAt(LocalDateTime.now());
        
        // Guardar todo
        logger.info("Guardando cambios de transferencia...");
        walletRepository.save(studentWallet);
        walletRepository.save(tutorWallet);
        transactionRepository.save(studentTransaction);
        transactionRepository.save(tutorTransaction);
        logger.info("Transferencia completada exitosamente");
        
        return Map.of(
            "success", true,
            "message", "Tokens transferidos exitosamente",
            "fromUserId", fromUserId,
            "toUserId", toUserId,
            "tokensTransferred", tokens,
            "studentNewBalance", studentWallet.getTokenBalance(),
            "tutorNewBalance", tutorWallet.getTokenBalance()
        );
    }
    
    /**
     * Reembolsa tokens al estudiante y los descuenta del tutor (cuando el tutor cancela la reservación)
     * @param studentUserId Sub de Cognito del estudiante (sin sufijo)
     * @param tutorUserId Sub de Cognito del tutor (sin sufijo)
     * @param tokens Cantidad de tokens a reembolsar
     * @param description Descripción del reembolso
     * @return Map con el resultado de la operación
     */
    public Map<String, Object> refundTokens(String studentUserId, String tutorUserId, Integer tokens, String description) {
        logger.info("refundTokens - studentUserId: {}, tutorUserId: {}, tokens: {}", studentUserId, tutorUserId, tokens);
        
        // Construir IDs de wallet con roles
        String studentWalletUserId = studentUserId + "-student";
        String tutorWalletUserId = tutorUserId + "-tutor";
        
        // Obtener wallet del estudiante
        Wallet studentWallet = walletRepository.findByUserId(studentWalletUserId)
            .orElseThrow(() -> new RuntimeException("Wallet del estudiante no encontrada: " + studentWalletUserId));
        
        // Obtener wallet del tutor
        Wallet tutorWallet = walletRepository.findByUserId(tutorWalletUserId)
            .orElseThrow(() -> new RuntimeException("Wallet del tutor no encontrada: " + tutorWalletUserId));
        
        // Verificar que el tutor tenga suficientes tokens
        if (!tutorWallet.useTokens(tokens)) {
            throw new RuntimeException("El tutor no tiene suficientes tokens para devolver");
        }
        
        // Agregar tokens al estudiante
        studentWallet.addTokens(tokens);
        
        // Crear transacción de reembolso para el estudiante (ingreso)
        Transaction studentRefundTransaction = new Transaction();
        studentRefundTransaction.setUserId(studentWalletUserId);
        studentRefundTransaction.setWalletId(studentWallet.getId());
        studentRefundTransaction.setType(Transaction.TransactionType.REFUND);
        studentRefundTransaction.setTokensAmount(tokens);
        studentRefundTransaction.setMoneyAmount(0.0);
        studentRefundTransaction.setDescription(description);
        studentRefundTransaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        studentRefundTransaction.setCreatedAt(LocalDateTime.now());
        studentRefundTransaction.setCompletedAt(LocalDateTime.now());
        
        // Crear transacción de egreso para el tutor
        Transaction tutorDeductionTransaction = new Transaction();
        tutorDeductionTransaction.setUserId(tutorWalletUserId);
        tutorDeductionTransaction.setWalletId(tutorWallet.getId());
        tutorDeductionTransaction.setType(Transaction.TransactionType.USAGE);
        tutorDeductionTransaction.setTokensAmount(tokens);
        tutorDeductionTransaction.setMoneyAmount(0.0);
        tutorDeductionTransaction.setDescription("Devolución por cancelación - " + description);
        tutorDeductionTransaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        tutorDeductionTransaction.setCreatedAt(LocalDateTime.now());
        tutorDeductionTransaction.setCompletedAt(LocalDateTime.now());
        
        // Guardar cambios
        logger.info("Guardando reembolso...");
        walletRepository.save(studentWallet);
        walletRepository.save(tutorWallet);
        transactionRepository.save(studentRefundTransaction);
        transactionRepository.save(tutorDeductionTransaction);
        logger.info("Reembolso completado exitosamente");
        
        return Map.of(
            "success", true,
            "message", "Tokens reembolsados exitosamente",
            "studentUserId", studentUserId,
            "tutorUserId", tutorUserId,
            "tokensRefunded", tokens,
            "studentNewBalance", studentWallet.getTokenBalance(),
            "tutorNewBalance", tutorWallet.getTokenBalance()
        );
    }
}
