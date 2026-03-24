package depth.finvibe.modules.market.infra.websocket.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

import depth.finvibe.modules.market.application.port.in.CurrentPriceCommandUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.time.Duration;

@Slf4j
@Component
public class MarketWebSocketRegistry {
  private final CurrentPriceCommandUseCase currentPriceCommandUseCase;
  private final TaskScheduler taskScheduler;
  private final MeterRegistry meterRegistry;

  private final Map<String, MarketWebSocketConnection> connections = new ConcurrentHashMap<>();
  private final Map<Long, Set<String>> topicSubscribers = new ConcurrentHashMap<>();
  private final Map<UUID, Map<Long, Integer>> userSubscriptions = new ConcurrentHashMap<>();

  @Value("${market.ws.session.send-time-limit-ms:1000}")
  private int sessionSendTimeLimitMs;

  @Value("${market.ws.session.buffer-size-limit-bytes:262144}")
  private int sessionBufferSizeLimitBytes;

  private ScheduledFuture<?> ttlRefreshTask;
  private Counter subscriptionsRejectedCounter;
  private Counter subscriptionsLimitExceededCounter;
  
  // TTL 갱신 주기: 5분 (TTL 10분의 절반)
  private static final long TTL_REFRESH_INTERVAL_MS = 5 * 60 * 1000L;
  
  public MarketWebSocketRegistry(
      @Lazy CurrentPriceCommandUseCase currentPriceCommandUseCase,
      TaskScheduler taskScheduler,
      MeterRegistry meterRegistry
  ) {
    this.currentPriceCommandUseCase = currentPriceCommandUseCase;
    this.taskScheduler = taskScheduler;
    this.meterRegistry = meterRegistry;
  }
  
  @PostConstruct
  public void startTtlRefreshScheduler() {
    ttlRefreshTask = taskScheduler.scheduleAtFixedRate(
        this::refreshSubscriptionTtl,
        Duration.ofMillis(TTL_REFRESH_INTERVAL_MS)
    );
    log.info("구독 TTL 갱신 스케줄러 시작 - 갱신 주기: {}분", TTL_REFRESH_INTERVAL_MS / 60000);

    Gauge.builder("ws.connections.active", connections, Map::size)
        .description("활성 WebSocket 연결 수")
        .register(meterRegistry);
    Gauge.builder("ws.connections.authenticated", connections,
            map -> map.values().stream().filter(c -> c.getState().isAuthenticated()).count())
        .description("인증된 WebSocket 연결 수")
        .register(meterRegistry);
    Gauge.builder("ws.users.active", userSubscriptions, Map::size)
        .description("활성 WebSocket 사용자 수")
        .register(meterRegistry);
    Gauge.builder("ws.topics.active", topicSubscribers, Map::size)
        .description("구독 중인 토픽(종목) 수")
        .register(meterRegistry);
    subscriptionsRejectedCounter = Counter.builder("ws.subscriptions.rejected")
        .description("구독 거부 횟수 (한도 초과)")
        .register(meterRegistry);
    subscriptionsLimitExceededCounter = Counter.builder("ws.subscriptions.limit.exceeded")
        .description("사용자별 구독 한도 초과 이벤트 수")
        .register(meterRegistry);
    Gauge.builder("ws.session.send.failures.total", connections,
            map -> map.values().stream().mapToDouble(MarketWebSocketConnection::getTotalSendFailures).sum())
        .description("활성 세션 전체의 WebSocket 전송 실패 누적 수")
        .register(meterRegistry);
    Gauge.builder("ws.session.send.failures.max", connections,
            map -> map.values().stream().mapToDouble(MarketWebSocketConnection::getTotalSendFailures).max().orElse(0))
        .description("활성 세션 중 최대 WebSocket 전송 실패 수")
        .register(meterRegistry);
  }
  
  @PreDestroy
  public void stopTtlRefreshScheduler() {
    if (ttlRefreshTask != null) {
      ttlRefreshTask.cancel(false);
      log.info("구독 TTL 갱신 스케줄러 종료");
    }
  }
  
  /**
   * 현재 활성화된 모든 구독에 대해 TTL 갱신
   */
  private void refreshSubscriptionTtl() {
    int totalRefreshed = 0;
    for (Map.Entry<UUID, Map<Long, Integer>> entry : userSubscriptions.entrySet()) {
      UUID userId = entry.getKey();
      Map<Long, Integer> topics = entry.getValue();
      
      for (Long stockId : topics.keySet()) {
        try {
          currentPriceCommandUseCase.renewWatchingStock(stockId, userId);
          totalRefreshed++;
        } catch (Exception ex) {
          log.error("TTL 갱신 실패 - stockId: {}, userId: {}", stockId, userId, ex);
        }
      }
    }
    if (totalRefreshed > 0) {
      log.debug("구독 TTL 갱신 완료 - 갱신 건수: {}", totalRefreshed);
    }
  }

    public MarketWebSocketConnection register(WebSocketSession session) {
        WebSocketSession decoratedSession = new ConcurrentWebSocketSessionDecorator(
            session,
            sessionSendTimeLimitMs,
            sessionBufferSizeLimitBytes
        );
        MarketWebSocketConnection connection = new MarketWebSocketConnection(decoratedSession);
        connections.put(decoratedSession.getId(), connection);
        updateConnectionGauges();
        return connection;
    }

    public MarketWebSocketConnection getConnection(String sessionId) {
        return connections.get(sessionId);
    }

  public void remove(String sessionId) {
    MarketWebSocketConnection connection = connections.remove(sessionId);
    if (connection == null) {
      return;
    }
    CustomWebSocketSession state = connection.getState();
    UUID userId = connection.getUserId();

    // 1. 사용자 기반 구독 인덱스 정리
    if (userId != null) {
      Map<Long, Integer> userTopics = userSubscriptions.get(userId);
      if (userTopics != null) {
        for (Long stockId : state.getSubscribedStockIds()) {
          decrementUserTopic(userTopics, stockId);
        }
        if (userTopics.isEmpty()) {
          userSubscriptions.remove(userId);
        }
      }
    }

    // 2. 토픽 기반 구독자 인덱스 정리 (가장 중요한 부분: Fanout 대상에서 즉시 제외)
    for (Long stockId : state.getSubscribedStockIds()) {
      Set<String> subscribers = topicSubscribers.get(stockId);
      if (subscribers != null) {
        subscribers.remove(sessionId);
        if (subscribers.isEmpty()) {
          topicSubscribers.remove(stockId);
        }
      }
    }

    // 3. 내부 상태 참조 해제 (명시적 null 처리로 GC 지원)
    state.getSubscribedStockIds().clear();
    log.debug("WebSocket 세션 등록 해제 완료 - sessionId: {}", sessionId);

    updateConnectionGauges();
  }

    public void authenticate(MarketWebSocketConnection connection, UUID userId) {
        connection.getState().authenticate(userId);
        userSubscriptions.computeIfAbsent(userId, key -> new ConcurrentHashMap<>());
    }

  public SubscribeResult subscribe(MarketWebSocketConnection connection, List<String> topics, int limit) {
    CustomWebSocketSession state = connection.getState();
    UUID userId = connection.getUserId();
    if (userId == null) {
      return SubscribeResult.unauthorized();
    }

    Map<Long, Integer> userTopics = userSubscriptions.computeIfAbsent(userId, key -> new ConcurrentHashMap<>());
    int uniqueTopicCount = userTopics.size();
    List<String> subscribed = new ArrayList<>();
    List<String> alreadySubscribed = new ArrayList<>();
    List<String> rejected = new ArrayList<>();
    boolean limitExceeded = false;

    for (String topic : topics) {
      Long stockId = extractStockId(topic);
      if (stockId == null) {
        continue;
      }

      if (state.getSubscribedStockIds().contains(stockId)) {
        alreadySubscribed.add(topic);
        continue;
      }
      if (!userTopics.containsKey(stockId) && uniqueTopicCount >= limit) {
        rejected.add(topic);
        limitExceeded = true;
        subscriptionsRejectedCounter.increment();
        continue;
      }
      state.getSubscribedStockIds().add(stockId);
      boolean isNewTopic = !userTopics.containsKey(stockId);
      userTopics.merge(stockId, 1, Integer::sum);
      if (isNewTopic) {
        uniqueTopicCount++;
        // 새로운 종목 구독 시 registerWatchingStock 호출
        try {
          currentPriceCommandUseCase.registerWatchingStock(stockId, userId);
          log.debug("종목 실시간 감시 등록 - stockId: {}, userId: {}", stockId, userId);
        } catch (Exception ex) {
          log.error("종목 실시간 감시 등록 실패 - stockId: {}, userId: {}", stockId, userId, ex);
        }
      }
      topicSubscribers.computeIfAbsent(stockId, key -> ConcurrentHashMap.newKeySet()).add(connection.getSession().getId());
      subscribed.add(topic);
    }

    if (limitExceeded) {
      subscriptionsLimitExceededCounter.increment();
    }
    return new SubscribeResult(subscribed, alreadySubscribed, rejected, limitExceeded);
  }

  public UnsubscribeResult unsubscribe(MarketWebSocketConnection connection, List<String> topics) {
    CustomWebSocketSession state = connection.getState();
    UUID userId = connection.getUserId();
    List<String> unsubscribed = new ArrayList<>();
    List<String> notSubscribed = new ArrayList<>();

    for (String topic : topics) {
      Long stockId = extractStockId(topic);
      if (stockId == null || !state.getSubscribedStockIds().contains(stockId)) {
        notSubscribed.add(topic);
        continue;
      }
      state.getSubscribedStockIds().remove(stockId);
      unsubscribed.add(topic);

      Set<String> subscribers = topicSubscribers.get(stockId);
      if (subscribers != null) {
        subscribers.remove(connection.getSession().getId());
        if (subscribers.isEmpty()) {
          topicSubscribers.remove(stockId);
        }
      }
      if (userId != null) {
        Map<Long, Integer> userTopics = userSubscriptions.get(userId);
        if (userTopics != null) {
          boolean wasLastSubscription = decrementUserTopic(userTopics, stockId);
          // 해당 유저의 해당 종목 구독이 모두 해제된 경우
          if (wasLastSubscription) {
            try {
              currentPriceCommandUseCase.unregisterWatchingStock(stockId, userId);
              log.debug("종목 실시간 감시 해제 - stockId: {}, userId: {}", stockId, userId);
            } catch (Exception ex) {
              log.error("종목 실시간 감시 해제 실패 - stockId: {}, userId: {}", stockId, userId, ex);
            }
          }
          if (userTopics.isEmpty()) {
            userSubscriptions.remove(userId);
          }
        }
      }
    }

    return new UnsubscribeResult(unsubscribed, notSubscribed);
  }

    public List<MarketWebSocketConnection> getSubscribers(String topic) {
        Long stockId = extractStockId(topic);
        if (stockId == null) {
            return List.of();
        }
        Set<String> subscriberIds = topicSubscribers.get(stockId);
        if (subscriberIds == null || subscriberIds.isEmpty()) {
            return List.of();
        }

        // 할당 오버헤드를 줄이기 위해 ArrayList 크기를 미리 지정
        List<MarketWebSocketConnection> result = new ArrayList<>(subscriberIds.size());
        for (String sessionId : subscriberIds) {
            MarketWebSocketConnection connection = connections.get(sessionId);
            if (connection != null) {
                result.add(connection);
            }
        }
        return result;
    }

    public String[] snapshotSubscriberIds(String topic) {
        Long stockId = extractStockId(topic);
        if (stockId == null) {
            return new String[0];
        }
        Set<String> subscriberIds = topicSubscribers.get(stockId);
        if (subscriberIds == null || subscriberIds.isEmpty()) {
            return new String[0];
        }
        return subscriberIds.toArray(String[]::new);
    }

    public record SubscribeResult(
            List<String> subscribed,
            List<String> alreadySubscribed,
            List<String> rejected,
            boolean limitExceeded
    ) {
        static SubscribeResult unauthorized() {
            return new SubscribeResult(List.of(), List.of(), List.of(), false);
        }
    }

    public record UnsubscribeResult(
            List<String> unsubscribed,
            List<String> notSubscribed
    ) {}

  private boolean decrementUserTopic(Map<Long, Integer> userTopics, Long stockId) {
    Integer count = userTopics.get(stockId);
    if (count == null) {
      return false;
    }
    if (count <= 1) {
      userTopics.remove(stockId);
      return true; // 마지막 구독이 해제됨
    } else {
      userTopics.put(stockId, count - 1);
      return false; // 아직 다른 세션에서 구독 중
    }
  }

  public void updateConnectionGauges() {
    // connection-related gauges read directly from the backing map; no per-session meter updates needed.
  }

  public void forEachConnection(Consumer<MarketWebSocketConnection> consumer) {
    connections.values().forEach(consumer);
  }

  /**
   * topic에서 stockId 추출
   * topic 형식: "quote:{stockId}"
   */
  private Long extractStockId(String topic) {
    if (topic == null || !topic.startsWith("quote:")) {
      return null;
    }
    try {
      return Long.parseLong(topic.substring(6));
    } catch (NumberFormatException ex) {
      log.warn("Invalid topic format: {}", topic);
      return null;
    }
  }
}
