package com.messenger.gateway.service;



import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceClient {

    @Value("${auth-service.url}")
    private String authServiceUrl;

    private final RestTemplate restTemplate;

    public Long validateAndGetUserId(String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            HttpEntity<?> entity = new HttpEntity<>(headers);


            ResponseEntity<Long> response = restTemplate.exchange(
                    authServiceUrl + "/api/auth/me",  // Правильный путь!
                    HttpMethod.GET,
                    entity,
                    Long.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Token validation failed", e);
            return null;
        }
    }
}
