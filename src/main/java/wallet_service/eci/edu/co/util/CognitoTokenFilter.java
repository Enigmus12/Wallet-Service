package wallet_service.eci.edu.co.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class CognitoTokenFilter extends OncePerRequestFilter {

    private final CognitoTokenDecoder cognitoTokenDecoder;

    @Autowired
    public CognitoTokenFilter(CognitoTokenDecoder cognitoTokenDecoder) {
        this.cognitoTokenDecoder = cognitoTokenDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // Permitir ciertos endpoints sin autenticación
        String requestPath = request.getRequestURI();
        
        if (shouldSkipFilter(requestPath)) {
            chain.doFilter(request, response);
            return;
        }

        final String authorizationHeader = request.getHeader("Authorization");

        String sub = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                // Validar que el token de Cognito sea válido
                if (cognitoTokenDecoder != null && cognitoTokenDecoder.isTokenValid(jwt)) {
                    CognitoTokenDecoder.CognitoUserInfo userInfo = cognitoTokenDecoder.extractUserInfo(jwt);
                    sub = userInfo.getSub();
                }
            } catch (Exception e) {
                logger.error("Error validating Cognito token", e);
            }
        }

        if (sub != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                // Ya no usamos el rol del token, asignar rol básico
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        sub, null, authorities);

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (Exception e) {
                logger.error("Error setting up security context with Cognito token", e);
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Determina si el filtro debe saltarse para ciertos endpoints
     */
    private boolean shouldSkipFilter(String requestPath) {
        return requestPath.equals("/Api-user/process-cognito-user") ||
               requestPath.equals("/Api-user/users") ||
               requestPath.equals("/Api-user/public/") ||   
               requestPath.startsWith("/Api-search/");
    }
}