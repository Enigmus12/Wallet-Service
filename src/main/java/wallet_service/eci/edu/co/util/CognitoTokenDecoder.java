package wallet_service.eci.edu.co.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class CognitoTokenDecoder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Decodifica un token JWT de AWS Cognito sin verificar la firma
     * @param token Token JWT de Cognito
     * @return Claims del token
     * @throws JwtException si el token es inválido
     */
    public Claims decodeToken(String token) throws JwtException {
        try {
            // Remover "Bearer " si está presente
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            // Parsear el token sin verificar la firma (ya que Cognito la maneja)
            // En un entorno de producción, deberías verificar la firma con la clave pública de Cognito
            return Jwts.parserBuilder()
                    .build()
                    .parseClaimsJwt(token.substring(0, token.lastIndexOf('.') + 1))
                    .getBody();
        } catch (Exception e) {
            throw new JwtException("Token inválido: " + e.getMessage());
        }
    }

    /**
     * Extrae información específica del usuario desde el token de Cognito
     * @param token Token JWT de Cognito
     * @return CognitoUserInfo con los datos del usuario
     */
    public CognitoUserInfo extractUserInfo(String token) {
        try {
            // Separar las partes del JWT
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new JwtException("Token JWT malformado");
            }

            // Decodificar el payload (segunda parte)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode jsonNode = objectMapper.readTree(payload);

            CognitoUserInfo userInfo = new CognitoUserInfo();
            userInfo.setSub(jsonNode.get("sub").asText());
            userInfo.setEmail(jsonNode.get("email").asText());
            userInfo.setName(jsonNode.get("name").asText());
            
            // Ya no extraemos el rol del token, viene del frontend

            // Teléfono opcional
            JsonNode phoneNode = jsonNode.get("phone_number");
            if (phoneNode != null) {
                userInfo.setPhoneNumber(phoneNode.asText());
            }

            // Nickname opcional
            JsonNode nicknameNode = jsonNode.get("nickname");
            if (nicknameNode != null) {
                userInfo.setNickname(nicknameNode.asText());
            }

            return userInfo;
        } catch (Exception e) {
            throw new JwtException("Error al extraer información del token: " + e.getMessage());
        }
    }

    /**
     * Valida si el token no ha expirado
     * @param token Token JWT de Cognito
     * @return true si el token es válido, false si ha expirado
     */
    public boolean isTokenValid(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode jsonNode = objectMapper.readTree(payload);
            
            JsonNode expNode = jsonNode.get("exp");
            if (expNode != null) {
                long exp = expNode.asLong();
                long currentTime = System.currentTimeMillis() / 1000;
                return exp > currentTime;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Clase interna para encapsular la información del usuario desde Cognito
     */
    public static class CognitoUserInfo {
        private String sub;
        private String email;
        private String name;
        private String phoneNumber;
        private String nickname;

        // Getters y Setters
        public String getSub() { return sub; }
        public void setSub(String sub) { this.sub = sub; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }

        @Override
        public String toString() {
            return "CognitoUserInfo{" +
                    "sub='" + sub + '\'' +
                    ", email='" + email + '\'' +
                    ", name='" + name + '\'' +
                    ", phoneNumber='" + phoneNumber + '\'' +
                    ", nickname='" + nickname + '\'' +
                    '}';
        }
    }
}