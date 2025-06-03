package com.lsm.idea_print.controller;

import com.lsm.idea_print.dto.request.saveAccessTokenRequest;
import com.lsm.idea_print.service.MetaTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AccessTokenGenerateController {
    private final MetaTokenService metaTokenService;

    @PostMapping("/manual-save")
    public ResponseEntity<String> saveManually(@RequestBody String accessToken) {
        metaTokenService.refreshToken(accessToken);
        return ResponseEntity.ok("저장 완료");
    }
}
