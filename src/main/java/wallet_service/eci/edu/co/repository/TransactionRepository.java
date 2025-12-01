package wallet_service.eci.edu.co.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import wallet_service.eci.edu.co.model.Transaction;
import wallet_service.eci.edu.co.model.Transaction.TransactionType;
import wallet_service.eci.edu.co.model.Transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {
    
    /**
     * Busca todas las transacciones de un usuario
     * @param userId ID del usuario de Cognito
     * @return Lista de transacciones del usuario
     */
    List<Transaction> findByUserIdOrderByCreatedAtDesc(String userId);
    
    /**
     * Busca todas las transacciones de una wallet
     * @param walletId ID de la wallet
     * @return Lista de transacciones de la wallet
     */
    List<Transaction> findByWalletIdOrderByCreatedAtDesc(String walletId);
    
    /**
     * Busca transacciones por tipo
     * @param userId ID del usuario
     * @param type Tipo de transacción
     * @return Lista de transacciones del tipo especificado
     */
    List<Transaction> findByUserIdAndTypeOrderByCreatedAtDesc(String userId, TransactionType type);
    
    /**
     * Busca transacciones por estado
     * @param userId ID del usuario
     * @param status Estado de la transacción
     * @return Lista de transacciones con el estado especificado
     */
    List<Transaction> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, TransactionStatus status);
    
    /**
     * Busca una transacción por el sessionId de Stripe
     * @param stripeSessionId ID de sesión de Stripe
     * @return Optional con la transacción si existe
     */
    Optional<Transaction> findByStripeSessionId(String stripeSessionId);
    
    /**
     * Busca transacciones en un rango de fechas
     * @param userId ID del usuario
     * @param startDate Fecha de inicio
     * @param endDate Fecha de fin
     * @return Lista de transacciones en el rango especificado
     */
    List<Transaction> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        String userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Busca transacciones por bookingId
     * @param bookingId ID de la reservación
     * @return Lista de transacciones asociadas a la reservación
     */
    List<Transaction> findByBookingId(String bookingId);

    /**
     * Busca la transacción de un usuario por bookingId y tipo
     */
    Optional<Transaction> findFirstByBookingIdAndUserIdAndType(String bookingId, String userId, TransactionType type);
}
