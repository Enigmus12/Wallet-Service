package wallet_service.eci.edu.co.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "transactions")
public class Transaction {
    
    @Id
    private String id;
    
    @Indexed
    private String userId; // sub de Cognito
    
    @Indexed
    private String walletId; // Referencia a la wallet
    
    private TransactionType type; // PURCHASE, USAGE, REFUND
    
    private Integer tokensAmount; // Cantidad de tokens involucrados
    
    private Double moneyAmount; // Cantidad en pesos (para compras)
    
    private String stripeSessionId; // ID de sesión de Stripe (si aplica)
    
    private String description; // Descripción de la transacción
    
    private TransactionStatus status; // PENDING, COMPLETED, FAILED, CANCELLED
    
    private String metadata; // Información adicional en formato JSON (opcional)

    @Indexed
    private String bookingId; // ID de la reservación asociada (para transfer/refund)
    
    private LocalDateTime createdAt;
    
    private LocalDateTime completedAt;
    
    // Enums para tipos y estados
    public enum TransactionType {
        PURCHASE,   // Compra de tokens
        USAGE,      // Uso de tokens (consumo)
        REFUND      // Reembolso
    }
    
    public enum TransactionStatus {
        PENDING,    // Transacción pendiente
        COMPLETED,  // Transacción completada
        FAILED,     // Transacción fallida
        CANCELLED   // Transacción cancelada
    }
    
    // Constructor para crear una compra de tokens
    public static Transaction createPurchase(String userId, String walletId, 
                                            Integer tokens, Double amount, 
                                            String stripeSessionId) {
        Transaction transaction = new Transaction();
        transaction.userId = userId;
        transaction.walletId = walletId;
        transaction.type = TransactionType.PURCHASE;
        transaction.tokensAmount = tokens;
        transaction.moneyAmount = amount;
        transaction.stripeSessionId = stripeSessionId;
        transaction.description = "Compra de " + tokens + " tokens";
        transaction.status = TransactionStatus.PENDING;
        transaction.createdAt = LocalDateTime.now();
        return transaction;
    }
    
    // Constructor para crear un uso de tokens
    public static Transaction createUsage(String userId, String walletId, 
                                         Integer tokens, String description) {
        Transaction transaction = new Transaction();
        transaction.userId = userId;
        transaction.walletId = walletId;
        transaction.type = TransactionType.USAGE;
        transaction.tokensAmount = tokens;
        transaction.moneyAmount = 0.0;
        transaction.description = description;
        transaction.status = TransactionStatus.COMPLETED;
        transaction.createdAt = LocalDateTime.now();
        transaction.completedAt = LocalDateTime.now();
        return transaction;
    }
    
    // Método para completar una transacción
    public void complete() {
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }
    
    // Método para fallar una transacción
    public void fail() {
        this.status = TransactionStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }
}
