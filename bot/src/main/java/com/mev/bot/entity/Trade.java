package com.mev.bot.entity;
// 1. Add these dependencies to pom.xml


import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "solana_trades")
public class Trade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String transactionLogs;

    private LocalDateTime timestamp;
    private boolean isLargeTrade;
    private double slippage;
    private String poolAddress;

    // Additional fields you might want
    private double tradeAmount;
    private String transactionHash;
}