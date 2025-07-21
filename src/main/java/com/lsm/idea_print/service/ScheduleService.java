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
    private final MetaTokenRepository metaTokenRepository;
    private final McpService mcpService;
    private final MultiTopicMcpService multiTopicMcpService;


    @Scheduled(cron = "0 0 9 * * *") // 매일 오전 9시 실행 - MCP Pipeline (Single Source)
    public void executeMcpPipeline() {
        mcpService.executeFullPipeline();
    }
    
    @Scheduled(cron = "0 0 12 * * *") // 매일 정오 12시 실행 - MCP Pipeline (All Sources)
    public void executeMcpPipelineAllSources() {
        mcpService.executeFullPipelineForAllSources();
    }
    
    @Scheduled(cron = "0 30 10 * * *") // 매일 오전 10시 30분 - Multi-Topic Pipeline
    public void executeMultiTopicPipeline() {
        multiTopicMcpService.executeFullPipelineForAllTopics();
    }
    
    @Scheduled(cron = "0 0 14 * * *") // 매일 오후 2시 - Cross-Topic Pipeline
    public void executeCrossTopicPipeline() {
        multiTopicMcpService.executeCrossTopicPipeline();
    }
    
    @Scheduled(cron = "0 0 16 * * *") // 매일 오후 4시 - Universal Pipeline
    public void executeUniversalPipeline() {
        multiTopicMcpService.executeUniversalPipeline();
    }
    
    // AI-specific pipeline
    @Scheduled(cron = "0 0 11 * * *") // 매일 오전 11시 - AI Topic Pipeline
    public void executeAiTopicPipeline() {
        multiTopicMcpService.executeTopicPipeline("ai");
    }
    
    // Future life-hacks pipeline (currently commented out as no life-hacks sources exist yet)
    // @Scheduled(cron = "0 0 15 * * *") // 매일 오후 3시 - Life Hacks Topic Pipeline
    // public void executeLifeHacksTopicPipeline() {
    //     multiTopicMcpService.executeTopicPipeline("life-hacks");
    // }

    @Scheduled(cron = "0 0 */3 * * *") // 매일 3시간마다 실행 (0시, 3시, 6시) - Legacy posting

    public void postDailyGptContent() {
        threadsPostService.postDailyContentForAllAccounts()
                .doOnNext(response -> {
                    long successCount = response.getData().stream().filter(PostResultResponse::isSuccess).count();
                    System.out.println("\u2705 스케줄 완료 - 성공: " + successCount + " / 전체: " + response.getData().size());
                })
                .subscribe();
    }
}
