package com.lsm.idea_print.service;

import com.lsm.idea_print.dto.request.SaveAccessTokenRequest;
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
    //TOdo: 이거 작동 되도록, 그리고 토큰만료로 인한 에러 시 이거 실행되도록
    public Mono<MetaToken> refreshToken(String accessToken, String prompt) {
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
                    return metaTokenRepository.save(token);
                });
    }

    public Long saveAccount(SaveAccessTokenRequest request){
        MetaToken metaToken = MetaToken.builder()
                .accessToken(request.getAccessToken())
                .prompt(request.getPrompt())
                .userId(request.getUserId())
                .build();
        metaTokenRepository.save(metaToken);
        return metaToken.getId();
    }

    public Long updateAccount(Long id, SaveAccessTokenRequest request) {
        MetaToken existing = metaTokenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("아이디 없음"));

        MetaToken updated = existing.toBuilder()
                .accessToken(request.getAccessToken())
                .prompt(request.getPrompt())
                .userId(request.getUserId())
                .build();

        metaTokenRepository.save(updated);
        return updated.getId();
    }
}
