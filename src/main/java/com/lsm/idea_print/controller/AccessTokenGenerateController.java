package com.lsm.idea_print.controller;

import com.lsm.idea_print.dto.request.SaveAccessTokenRequest;
import com.lsm.idea_print.service.MetaTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/token")
public class AccessTokenGenerateController {
    private final MetaTokenService metaTokenService;

    @PostMapping("/manual-save")
    public Mono<ResponseEntity<String>> saveManually(@RequestBody SaveAccessTokenRequest request) {
        return metaTokenService.refreshToken(request.getAccessToken(), request.getPrompt())
                .map(token -> ResponseEntity.ok("저장 완료"));
    }

    @PostMapping("/save_account")
    public ResponseEntity<Long> saveAccount(@RequestBody SaveAccessTokenRequest request) {
        return ResponseEntity.ok(metaTokenService.saveAccount(request));
    }

    @PatchMapping("/save_account/{id}")
    public ResponseEntity<Long> updateAccount(@PathVariable Long id, @RequestBody SaveAccessTokenRequest request){
        return ResponseEntity.ok(metaTokenService.updateAccount(id, request));
    }
}
