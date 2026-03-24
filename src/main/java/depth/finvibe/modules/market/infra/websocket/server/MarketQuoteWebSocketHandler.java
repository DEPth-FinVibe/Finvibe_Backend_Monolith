package depth.finvibe.modules.market.infra.websocket.server;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import depth.finvibe.modules.user.infra.security.JwtTokenProvider;
import java.util.UUID;
import depth.finvibe.modules.market.application.port.in.MarketQueryUseCase;
import depth.finvibe.modules.market.domain.MarketHours;
import depth.finvibe.modules.market.domain.enums.MarketStatus;
import depth.finvibe.modules.market.dto.CurrentPriceDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketQuoteWebSocketHandler extends TextWebSocketHandler {
    private static final Pattern TOPIC_PATTERN = Pattern.compile("^quote:\\d+$");
    private static final int MAX_SUBSCRIPTIONS = 30;

    private final MarketWebSocketRegistry registry;
    private final JwtTokenProvider jwtTokenProvider;
    private final MarketQueryUseCase marketQueryUseCase;
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;
    private final MeterRegistry meterRegistry;
    private final MarketWebSocketSessionSender sessionSender;

    @Value("${market.ws.auth-timeout-ms:5000}")
    private long authTimeoutMs;

    @Value("${market.ws.ping-interval-ms:25000}")
    private long pingIntervalMs;

    @Value("${market.ws.rate-limit-per-second:20}")
    private int rateLimitPerSecond;

    @Value("${market.provider:kis}")
    private String marketProvider;

    private Counter authTimeoutCounter;
    private Counter rateLimitViolationCounter;
    private Timer subscribeDurationTimer;
    private ScheduledFuture<?> heartbeatSweepTask;

    @PostConstruct
    public void initMetrics() {
        authTimeoutCounter = Counter.builder("ws.auth.timeout")
                .description("인증 타임아웃으로 종료된 연결 수")
                .register(meterRegistry);
        rateLimitViolationCounter = Counter.builder("ws.rate.limit.violations")
                .description("초당 메시지 한도 초과 횟수")
                .register(meterRegistry);
        subscribeDurationTimer = Timer.builder("ws.subscribe.duration")
                .description("subscribe 요청 처리 시간 (registry 등록 + Redis 호출 포함)")
                .publishPercentileHistogram()
                .register(meterRegistry);

        long sweepIntervalMs = Math.max(1000L, pingIntervalMs);
        heartbeatSweepTask = taskScheduler.scheduleAtFixedRate(this::sweepConnections, sweepIntervalMs);
    }

    @PreDestroy
    public void stopHeartbeatSweep() {
        if (heartbeatSweepTask != null) {
            heartbeatSweepTask.cancel(false);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        registry.register(session);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        MarketWebSocketConnection connection = registry.getConnection(session.getId());
        if (connection == null) {
            return;
        }
        if (!connection.tryConsume(rateLimitPerSecond)) {
            rateLimitViolationCounter.increment();
            sendError(session, null, "RATE_LIMITED", "Too many requests.", null);
            return;
        }

        JsonNode root = parseJson(session, message);
        if (root == null) {
            return;
        }

        String type = getText(root, "type");
        if (type == null) {
            sendError(session, getText(root, "request_id"), "INVALID_MESSAGE", "Missing message type.", null);
            return;
        }

        switch (type) {
            case "auth" -> handleAuth(session, connection, root);
            case "pong" -> handlePong(connection);
            case "subscribe" -> handleSubscribe(session, connection, root);
            case "unsubscribe" -> handleUnsubscribe(session, connection, root);
            default -> sendError(session, getText(root, "request_id"), "INVALID_MESSAGE", "Unknown message type.", null);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        registry.remove(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(session.getId());
    }

    private void handleAuth(WebSocketSession session, MarketWebSocketConnection connection, JsonNode root) {
        String token = getText(root, "token");
        if (token == null || token.isBlank()) {
            sendImmediateError(session, null, "UNAUTHORIZED", "Missing token.", null);
            closeSession(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            UUID userId = jwtTokenProvider.getUserId(token);
            registry.authenticate(connection, userId);
            connection.getState().resetPongCount();
            sendAuthAck(session);
        } catch (Exception ex) {
            sendImmediateError(session, null, "UNAUTHORIZED", "Invalid token.", null);
            closeSession(session, CloseStatus.POLICY_VIOLATION);
        }
    }

    private void handlePong(MarketWebSocketConnection connection) {
        if (!connection.getState().isAuthenticated()) {
            return;
        }
        connection.getState().resetPongCount();
    }

    private void handleSubscribe(WebSocketSession session, MarketWebSocketConnection connection, JsonNode root) {
        if (!connection.getState().isAuthenticated()) {
            sendError(session, getText(root, "request_id"), "UNAUTHORIZED", "Authentication required.", null);
            return;
        }

        String requestId = getText(root, "request_id");
        List<String> topics = parseTopics(root);
        if (topics == null) {
            sendError(session, requestId, "INVALID_MESSAGE", "Invalid topics format.", null);
            return;
        }

        TopicValidationResult topicValidation = validateTopics(topics);
        List<String> invalidTopics = topicValidation.invalidTopics();
        List<String> validTopics = topicValidation.validTopics();

        long subscribeStartNs = System.nanoTime();
        MarketWebSocketRegistry.SubscribeResult result = registry.subscribe(connection, validTopics, MAX_SUBSCRIPTIONS);
        subscribeDurationTimer.record(System.nanoTime() - subscribeStartNs, TimeUnit.NANOSECONDS);
        List<String> rejected = new ArrayList<>(result.rejected());
        if (!invalidTopics.isEmpty()) {
            rejected.addAll(invalidTopics);
            sendError(session, requestId, "INVALID_TOPIC", "Topic format is invalid.",
                    Map.of("topic", invalidTopics.get(0)));
        }

        if (result.limitExceeded()) {
            sendError(session, requestId, "SUBSCRIPTION_LIMIT_EXCEEDED", "Subscription limit exceeded.",
                    Map.of("limit", MAX_SUBSCRIPTIONS));
        }

        Map<String, Object> warning = buildMarketClosedWarning();
        sendSubscribeAck(session, requestId, result, rejected, warning);
        sendInitialPriceSnapshots(session, result.subscribed());
    }

    private void handleUnsubscribe(WebSocketSession session, MarketWebSocketConnection connection, JsonNode root) {
        if (!connection.getState().isAuthenticated()) {
            sendError(session, getText(root, "request_id"), "UNAUTHORIZED", "Authentication required.", null);
            return;
        }
        String requestId = getText(root, "request_id");
        List<String> topics = parseTopics(root);
        if (topics == null) {
            sendError(session, requestId, "INVALID_MESSAGE", "Invalid topics format.", null);
            return;
        }
        MarketWebSocketRegistry.UnsubscribeResult result = registry.unsubscribe(connection, topics);
        sendUnsubscribeAck(session, requestId, result);
    }

    private void sendPing(WebSocketSession session) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ping");
        payload.put("ts", Instant.now().toEpochMilli());
        sendMessage(session, payload);
    }

    private void sendAuthAck(WebSocketSession session) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "auth");
        payload.put("ok", true);
        payload.put("ts", Instant.now().toEpochMilli());
        sendMessage(session, payload);
    }

    private void sendSubscribeAck(
            WebSocketSession session,
            String requestId,
            MarketWebSocketRegistry.SubscribeResult result,
            List<String> rejected,
            Map<String, Object> warning
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "subscribe");
        payload.put("request_id", requestId);
        payload.put("subscribed", result.subscribed());
        payload.put("already_subscribed", result.alreadySubscribed());
        payload.put("rejected", rejected);
        if (warning != null) {
            payload.put("warning", warning);
        }
        sendMessage(session, payload);
    }

    private Map<String, Object> buildMarketClosedWarning() {
        if ("mock".equalsIgnoreCase(marketProvider)) {
            return null;
        }
        if (MarketHours.getCurrentStatus() == MarketStatus.OPEN) {
            return null;
        }
        Map<String, Object> warning = new HashMap<>();
        warning.put("code", "MARKET_CLOSED");
        warning.put("message", "Market is currently closed. Price updates will resume when market opens.");
        warning.put("market_status", MarketStatus.CLOSED.name());
        warning.put("fallback_api", "/market/stocks/closing-prices");
        return warning;
    }

    private void sendUnsubscribeAck(WebSocketSession session, String requestId, MarketWebSocketRegistry.UnsubscribeResult result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "unsubscribe");
        payload.put("ok", true);
        payload.put("request_id", requestId);
        payload.put("unsubscribed", result.unsubscribed());
        payload.put("not_subscribed", result.notSubscribed());
        sendMessage(session, payload);
    }

    private void sendError(WebSocketSession session, String requestId, String code, String message, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "error");
        payload.put("request_id", requestId);
        payload.put("code", code);
        payload.put("message", message);
        if (details != null) {
            payload.put("details", details);
        }
        sendMessage(session, payload);
    }

    private void sendImmediateError(WebSocketSession session, String requestId, String code, String message, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "error");
        payload.put("request_id", requestId);
        payload.put("code", code);
        payload.put("message", message);
        if (details != null) {
            payload.put("details", details);
        }
        sessionSender.sendImmediate(session, payload);
    }

    private void sendInitialPriceSnapshots(WebSocketSession session, List<String> subscribedTopics) {
        if (subscribedTopics == null || subscribedTopics.isEmpty()) {
            return;
        }

        List<Long> stockIds = subscribedTopics.stream()
                .map(this::extractStockId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (stockIds.isEmpty()) {
            return;
        }

        try {
            List<CurrentPriceDto.Response> prices = marketQueryUseCase.getCurrentPrices(stockIds);
            Map<Long, CurrentPriceDto.Response> priceByStockId = prices.stream()
                    .collect(Collectors.toMap(CurrentPriceDto.Response::getStockId, price -> price, (left, right) -> right));

            for (String topic : subscribedTopics) {
                Long stockId = extractStockId(topic);
                if (stockId == null) {
                    continue;
                }
                CurrentPriceDto.Response price = priceByStockId.get(stockId);
                if (price == null) {
                    continue;
                }
                sendMessage(session, buildInitialPriceEventPayload(topic, price));
            }
        } catch (Exception ex) {
            log.warn("Failed to send initial price snapshot for subscribed topics: {}", subscribedTopics, ex);
        }
    }

    private Map<String, Object> buildInitialPriceEventPayload(String topic, CurrentPriceDto.Response price) {
        Map<String, Object> data = new HashMap<>();
        data.put("stockId", price.getStockId());
        data.put("exchange", "KRX");
        data.put("price", price.getClose());
        data.put("open", price.getOpen());
        data.put("high", price.getHigh());
        data.put("low", price.getLow());
        data.put("prevDayChangePct", price.getPrevDayChangePct());
        data.put("volume", price.getVolume());
        data.put("value", price.getValue());
        data.put("initial", true);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event");
        payload.put("topic", topic);
        payload.put("ts", Instant.now().toEpochMilli());
        payload.put("data", data);
        return payload;
    }

  private void sendMessage(WebSocketSession session, Map<String, Object> payload) {
    MarketWebSocketConnection connection = registry.getConnection(session.getId());
    if (connection != null) {
      if (!sessionSender.sendControl(connection, payload)) {
        log.debug("Skipped websocket control message due to backpressure - sessionId: {}", session.getId());
      }
      return;
    }
    sessionSender.sendImmediate(session, payload);
  }

    private JsonNode parseJson(WebSocketSession session, WebSocketMessage<String> message) {
        try {
            return objectMapper.readTree(message.getPayload());
        } catch (Exception ex) {
            sendError(session, null, "INVALID_MESSAGE", "JSON parsing failed.", null);
            return null;
        }
    }

    private List<String> parseTopics(JsonNode root) {
        JsonNode topicsNode = root.get("topics");
        if (topicsNode == null || !topicsNode.isArray()) {
            return null;
        }
        List<String> topics = new ArrayList<>();
        topicsNode.forEach(node -> {
            if (node.isTextual()) {
                topics.add(node.asText());
            }
        });
        return topics;
    }

    private TopicValidationResult validateTopics(List<String> topics) {
        List<String> validTopics = new ArrayList<>(topics.size());
        List<String> invalidTopics = new ArrayList<>();
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();

        for (String topic : topics) {
            if (!isValidTopic(topic)) {
                invalidTopics.add(topic);
                continue;
            }
            if (deduplicated.add(topic)) {
                validTopics.add(topic);
            }
        }
        return new TopicValidationResult(validTopics, invalidTopics);
    }

    private boolean isValidTopic(String topic) {
        return topic != null && TOPIC_PATTERN.matcher(topic).matches();
    }

    private String getText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private Long extractStockId(String topic) {
        if (topic == null || !TOPIC_PATTERN.matcher(topic).matches()) {
            return null;
        }
        try {
            return Long.parseLong(topic.substring(6));
        } catch (NumberFormatException ex) {
            log.warn("Invalid topic format: {}", topic);
            return null;
        }
    }

    private void closeSession(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ex) {
            log.warn("Failed to close websocket session.", ex);
        }
    }

    private void sweepConnections() {
        registry.forEachConnection(connection -> {
            WebSocketSession session = connection.getSession();
            CustomWebSocketSession state = connection.getState();

            if (!session.isOpen()) {
                registry.remove(session.getId());
                return;
            }

            if (!state.isAuthenticated()) {
                if (state.isAuthenticationExpired(authTimeoutMs)) {
                    authTimeoutCounter.increment();
                    sendImmediateError(session, null, "UNAUTHORIZED", "Authentication timeout.", null);
                    closeSession(session, CloseStatus.POLICY_VIOLATION);
                }
                return;
            }

            if (state.shouldDisconnect()) {
                closeSession(session, CloseStatus.SESSION_NOT_RELIABLE);
                return;
            }

            sendPing(session);
            state.incrementMissedPong();
        });
    }

    private record TopicValidationResult(
            List<String> validTopics,
            List<String> invalidTopics
    ) {
    }
}
