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
@Document(collection = "wallets")
public class Wallet {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String userId; // sub de Cognito + rol (ej: "user123-student" o "user123-tutor")
    
    private String actualUserId; // sub de Cognito sin sufijo de rol
    
    private String role; // "STUDENT" o "TUTOR"
    
    private String email; // Email del usuario
    
    private Integer tokenBalance; // Saldo de tokens disponibles
    
    private Double totalSpent; // Total gastado en pesos
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    // Constructor para crear una nueva wallet con rol
    public Wallet(String actualUserId, String role, String email) {
        this.actualUserId = actualUserId;
        this.role = role;
        this.userId = actualUserId + "-" + role.toLowerCase();
        this.email = email;
        this.tokenBalance = 0;
        this.totalSpent = 0.0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Constructor legacy (sin rol) - mantiene compatibilidad
    public Wallet(String userId, String email) {
        this.userId = userId;
        this.actualUserId = userId;
        this.role = "STUDENT"; // Por defecto estudiante
        this.email = email;
        this.tokenBalance = 0;
        this.totalSpent = 0.0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Método para agregar tokens
    public void addTokens(Integer tokens) {
        if (tokens > 0) {
            this.tokenBalance += tokens;
            this.updatedAt = LocalDateTime.now();
        }
    }
    
    // Método para usar tokens
    public boolean useTokens(Integer tokens) {
        if (tokens > 0 && this.tokenBalance >= tokens) {
            this.tokenBalance -= tokens;
            this.updatedAt = LocalDateTime.now();
            return true;
        }
        return false;
    }
    
    // Método para agregar al total gastado
    public void addToTotalSpent(Double amount) {
        if (amount > 0) {
            this.totalSpent += amount;
            this.updatedAt = LocalDateTime.now();
        }
    }
}
