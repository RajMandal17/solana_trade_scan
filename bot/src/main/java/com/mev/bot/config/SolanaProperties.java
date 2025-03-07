package com.mev.bot.config;
// src/main/java/com/example/solana/config/SolanaProperties.java
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "solana")
@Getter
@Setter
public class SolanaProperties {
    private String wsUrl;
    private double minTradeAmount;
    private double maxTradeAmount;
    private double highSlippageThreshold;
    private String[] liquidityPools;

    // Getters and setters
}