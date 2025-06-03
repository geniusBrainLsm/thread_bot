package com.lsm.idea_print.service;

import com.lsm.idea_print.dto.response.MetaTokenResponse;
import com.lsm.idea_print.entity.MetaToken;
import com.lsm.idea_print.repository.MetaTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class MetaTokenService {

    private final MetaTokenRepository metaTokenRepository;
    private final WebClient webClient;

    @Value("${meta.app-id}")
    private String appId;

    @Value("${meta.app-secret}")
    private String appSecret;

    public MetaTokenService(WebClient.Builder builder, MetaTokenRepository metaTokenRepository) {
        this.metaTokenRepository = metaTokenRepository;
        this.webClient = builder
                .baseUrl("https://threads.net")
                .build();
    }

    public Mono<MetaToken> refreshToken(String accessToken) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/oauth/access_token")
                        .queryParam("grant_type", "authorization_code")
                        .queryParam("client_id", appId)
                        .queryParam("client_secret", appSecret)
                        .queryParam("redirect_uri", "https://localhost")
                        .queryParam("code", accessToken)
                        .build())
                .retrieve()
                .bodyToMono(MetaTokenResponse.class)
                .map(response -> {
                    MetaToken token = new MetaToken();
                    token.setAccessToken(response.getAccessToken());
                    token.setUserId(response.getUserId());
                    return metaTokenRepository.save(token);
                });
    }


}
