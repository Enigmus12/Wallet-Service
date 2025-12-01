package wallet_service.eci.edu.co.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "wallet_service.eci.edu.co.repository")
@EnableMongoAuditing
public class MongoConfig {
}
