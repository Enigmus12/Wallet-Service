package wallet_service.eci.edu.co.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import wallet_service.eci.edu.co.model.Wallet;

import java.util.Optional;
import java.util.List;

@Repository
public interface WalletRepository extends MongoRepository<Wallet, String> {
    
    /**
     * Busca una wallet por el userId (sub de Cognito)
     * @param userId ID del usuario de Cognito
     * @return Optional con la wallet si existe
     */
    Optional<Wallet> findByUserId(String userId);

    /**
     * Devuelve todas las wallets que coinciden con el userId (no debería haber más de una).
     * Se usa para detectar y limpiar duplicados cuando existen antes de que se aplique el índice único.
     */
    List<Wallet> findAllByUserId(String userId);
    
    /**
     * Verifica si existe una wallet para el userId
     * @param userId ID del usuario de Cognito
     * @return true si existe, false en caso contrario
     */
    boolean existsByUserId(String userId);
    
    /**
     * Busca una wallet por email
     * @param email Email del usuario
     * @return Optional con la wallet si existe
     */
    Optional<Wallet> findByEmail(String email);
}
