package com.mev.bot.client;

import com.mev.bot.config.SolanaProperties;
import com.mev.bot.entity.Trade;
import com.mev.bot.repo.TradeRepository;
import jakarta.websocket.*;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;

@Slf4j
@Component
@ClientEndpoint
public class SolanaWebSocketClient {

    private final SolanaProperties properties;
    private Session session;

    private final TradeRepository tradeRepository;

    @Autowired
    public SolanaWebSocketClient(SolanaProperties properties, TradeRepository tradeRepository) {
        this.properties = properties;
        this.tradeRepository = tradeRepository;
        connect();
    }

    private void connect() {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, URI.create(properties.getWsUrl()));
        } catch (Exception e) {
            log.error("Connection error: {}", e.getMessage());
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        log.info("✅ Connected to Solana WebSocket");
        sendSubscription();
    }

    private void sendSubscription() {
        JSONObject subscription = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "logsSubscribe")
                .put("params", new JSONArray()
                        .put(new JSONObject().put("mentions", new JSONArray(properties.getLiquidityPools())))
                        .put(new JSONObject().put("commitment", "processed")));

        sendMessage(subscription.toString());
        log.info("📡 Sent subscription request");
    }

    @OnMessage
    public void onMessage(String message) {
        JSONObject response = new JSONObject(message);
        if (response.has("error")) {
            log.error("❌ Subscription error: {}", response.get("error"));
            return;
        }

        if ("logsNotification".equals(response.optString("method"))) {
            JSONObject result = response.getJSONObject("params")
                    .getJSONObject("result")
                    .getJSONObject("value");
            processTransaction(result.getJSONArray("logs"));
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        log.error("WebSocket error: {}", throwable.getMessage());
    }

    @OnClose
    public void onClose(CloseReason closeReason) {
        log.warn("Connection closed: {}. Reconnecting...", closeReason);
        reconnect();
    }

    private void sendMessage(String message) {
        try {
            if (session != null && session.isOpen()) {
                session.getBasicRemote().sendText(message);
            }
        } catch (IOException e) {
            log.error("Error sending message: {}", e.getMessage());
        }
    }

    private void processTransaction(JSONArray logs) {
        log.info("🔍 Processing transaction logs: {}", logs);

        Trade trade = new Trade();
        trade.setTimestamp(LocalDateTime.now());
        trade.setTransactionLogs(logs.toString());

        boolean largeTrade = false;
        double slippage = 0;
        String poolAddress = "";

        for (int i = 0; i < logs.length(); i++) {
            String logEntry = logs.getString(i);

            if (logEntry.contains("Swap") || logEntry.contains("Trade")) {
                largeTrade = true;
            }

            if (logEntry.contains("slippage")) {
                slippage = extractSlippage(logEntry);
            }

            if (logEntry.contains("Tokenkeg")) {
                poolAddress = extractPoolAddress(logEntry);
            }
        }

        trade.setLargeTrade(largeTrade);
        trade.setSlippage(slippage);
        trade.setPoolAddress(poolAddress);

        try {
            Trade savedTrade = tradeRepository.save(trade);
            log.info("💾 Saved trade to database: ID {}", savedTrade.getId());
        } catch (Exception e) {
            log.error("❌ Error saving trade: {}", e.getMessage());
        }

        if (largeTrade) {
            log.warn("💰 Large trade detected");
        }

        if (slippage > properties.getHighSlippageThreshold()) {
            log.warn("⚠️ High slippage detected: {}%", slippage);
        }
    }

    private String extractPoolAddress(String logEntry) {
        try {
            if (logEntry.contains("Tokenkeg")) {
                String[] parts = logEntry.split(" ");
                for (String part : parts) {
                    if (part.length() > 20) {
                        return part;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error extracting pool address: {}", e.getMessage());
        }
        return "unknown";
    }

    private double extractSlippage(String logEntry) {
        try {
            String[] parts = logEntry.split("slippage: ");
            if (parts.length > 1) {
                return Double.parseDouble(parts[1].replaceAll("[^\\d.]", ""));
            }
        } catch (NumberFormatException e) {
            log.error("Error parsing slippage: {}", logEntry);
        }
        return 0.0;
    }

    private void reconnect() {
        try {
            if (session != null) {
                session.close();
            }
            connect();
        } catch (IOException e) {
            log.error("Error during reconnect: {}", e.getMessage());
        }
    }
}