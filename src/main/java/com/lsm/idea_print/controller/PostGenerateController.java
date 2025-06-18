package com.lsm.idea_print.controller;

import com.lsm.idea_print.dto.ApiResponse;
import com.lsm.idea_print.dto.request.SaveAccessTokenRequest;
import com.lsm.idea_print.dto.response.PostResultResponse;
import com.lsm.idea_print.service.MetaTokenService;
import com.lsm.idea_print.service.ThreadsPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/post")
@RequiredArgsConstructor
public class PostGenerateController {

    private final ThreadsPostService threadsPostService;


    @PostMapping("/run")
    public Mono<ApiResponse<List<PostResultResponse>>> postDailyGptContent() {
        return threadsPostService.postDailyContentForAllAccounts();
    }
    // 사용자 정보 확인 엔드포인트 추가
    @GetMapping("/user-info")
    public Mono<ResponseEntity<Object>> getUserInfo(@RequestParam String accessToken) {
        return threadsPostService.getUserInfo(accessToken)
                .map(response -> ResponseEntity.ok((Object) response))
                .onErrorResume(error -> {
                    String errorMessage = "❌ 사용자 정보 가져오기 실패: " + error.getMessage();
                    System.err.println(errorMessage);
                    return Mono.just(ResponseEntity.badRequest().body(errorMessage));
                });
    }





}
