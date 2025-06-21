package com.lsm.idea_print.controller;

import com.lsm.idea_print.service.ScheduleService;
import com.lsm.idea_print.service.SeuhariService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/menual")
public class manualSeuhariController {
    private final SeuhariService seuhariService;

    @PostMapping
    public Mono<List<Long>> manualSeuhari(){
        return Mono.just(seuhariService.performDailyShariForCommenters());
    }
}
