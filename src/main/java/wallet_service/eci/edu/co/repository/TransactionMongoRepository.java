package wallet_service.eci.edu.co.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import wallet_service.eci.edu.co.model.Transaction;

@Repository
public interface TransactionMongoRepository extends MongoRepository<Transaction, String> {
    
}
