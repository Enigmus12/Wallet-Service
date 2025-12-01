package wallet_service.eci.edu.co.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {
    private String userId;
    private String email;
    private Integer tokenBalance;
    private Double totalSpent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
