package com.lsm.idea_print.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "threads")
public class ThreadsAccountProperties {
    private List<Account> accounts;

    @Data
    public static class Account {
        private String userId;
        private String accessToken;
    }
}