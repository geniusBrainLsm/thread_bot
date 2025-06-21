package com.lsm.idea_print.service;

import com.lsm.idea_print.dto.response.PostResultResponse;
import com.lsm.idea_print.entity.MetaToken;
import com.lsm.idea_print.repository.MetaTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
@Service
@RequiredArgsConstructor
public class ScheduleService {
    private final ThreadsPostService threadsPostService;
    @Scheduled(cron = "0 0 */3 * * *") // 매일 3시간마다 실행 (0시, 3시, 6시
    public void postDailyGptContent() {
        threadsPostService.postDailyContentForAllAccounts()
                .doOnNext(response -> {
                    long successCount = response.getData().stream().filter(PostResultResponse::isSuccess).count();
                    System.out.println("\u2705 스케줄 완료 - 성공: " + successCount + " / 전체: " + response.getData().size());
                })
                .subscribe();
    }
}
