package depth.finvibe.modules.market.infra.websocket.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import depth.finvibe.modules.market.application.port.in.CurrentPriceCommandUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ScheduledFuture;
import java.time.Duration;

@Slf4j
@Component
public class MarketWebSocketRegistry {
  private final CurrentPriceCommandUseCase currentPriceCommandUseCase;
  private final TaskScheduler taskScheduler;
  private final MeterRegistry meterRegistry;

  private final Map<String, MarketWebSocketConnection> connections = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> topicSubscribers = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, Integer>> userSubscriptions = new ConcurrentHashMap<>();

  private ScheduledFuture<?> ttlRefreshTask;
  private Counter subscriptionsRejectedCounter;
  private Counter subscriptionsLimitExceededCounter;
  private MultiGauge topicSubscriberGauge;
  
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
    topicSubscriberGauge = MultiGauge.builder("ws.topic.subscriber.count")
        .description("종목별 WebSocket 구독자 수")
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
    for (Map.Entry<UUID, Map<String, Integer>> entry : userSubscriptions.entrySet()) {
      UUID userId = entry.getKey();
      Map<String, Integer> topics = entry.getValue();
      
      for (String topic : topics.keySet()) {
        Long stockId = extractStockId(topic);
        if (stockId != null) {
          try {
            currentPriceCommandUseCase.registerWatchingStock(stockId, userId);
            totalRefreshed++;
          } catch (Exception ex) {
            log.error("TTL 갱신 실패 - stockId: {}, userId: {}", stockId, userId, ex);
          }
        }
      }
    }
    if (totalRefreshed > 0) {
      log.debug("구독 TTL 갱신 완료 - 갱신 건수: {}", totalRefreshed);
    }
  }

    public MarketWebSocketConnection register(WebSocketSession session) {
        MarketWebSocketConnection connection = new MarketWebSocketConnection(session);
        connections.put(session.getId(), connection);
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
    if (userId != null) {
      Map<String, Integer> userTopics = userSubscriptions.get(userId);
      if (userTopics != null) {
        for (String topic : state.getSubscribedTopics()) {
          boolean wasLastSubscription = decrementUserTopic(userTopics, topic);
          // 해당 유저의 해당 종목 구독이 모두 해제된 경우
          if (wasLastSubscription) {
            Long stockId = extractStockId(topic);
            if (stockId != null) {
              try {
                currentPriceCommandUseCase.unregisterWatchingStock(stockId, userId);
                log.debug("연결 해제로 인한 종목 실시간 감시 해제 - stockId: {}, userId: {}", stockId, userId);
              } catch (Exception ex) {
                log.error("연결 해제로 인한 종목 실시간 감시 해제 실패 - stockId: {}, userId: {}", stockId, userId, ex);
              }
            }
          }
        }
        if (userTopics.isEmpty()) {
          userSubscriptions.remove(userId);
        }
      }
    }
    for (String topic : state.getSubscribedTopics()) {
      Set<String> subscribers = topicSubscribers.get(topic);
      if (subscribers != null) {
        subscribers.remove(sessionId);
        if (subscribers.isEmpty()) {
          topicSubscribers.remove(topic);
        }
      }
    }
    updateTopicSubscriberGauge();
  }

    public void authenticate(MarketWebSocketConnection connection, UUID userId) {
        connection.getState().authenticate(userId.toString());
        userSubscriptions.computeIfAbsent(userId, key -> new ConcurrentHashMap<>());
    }

  public SubscribeResult subscribe(MarketWebSocketConnection connection, List<String> topics, int limit) {
    CustomWebSocketSession state = connection.getState();
    UUID userId = connection.getUserId();
    if (userId == null) {
      return SubscribeResult.unauthorized();
    }

    Map<String, Integer> userTopics = userSubscriptions.computeIfAbsent(userId, key -> new ConcurrentHashMap<>());
    int uniqueTopicCount = userTopics.size();
    List<String> subscribed = new ArrayList<>();
    List<String> alreadySubscribed = new ArrayList<>();
    List<String> rejected = new ArrayList<>();
    boolean limitExceeded = false;

    for (String topic : topics) {
      if (state.getSubscribedTopics().contains(topic)) {
        alreadySubscribed.add(topic);
        continue;
      }
      if (!userTopics.containsKey(topic) && uniqueTopicCount >= limit) {
        rejected.add(topic);
        limitExceeded = true;
        subscriptionsRejectedCounter.increment();
        continue;
      }
      state.getSubscribedTopics().add(topic);
      boolean isNewTopic = !userTopics.containsKey(topic);
      userTopics.merge(topic, 1, Integer::sum);
      if (isNewTopic) {
        uniqueTopicCount++;
        // 새로운 종목 구독 시 registerWatchingStock 호출
        Long stockId = extractStockId(topic);
        if (stockId != null) {
          try {
            currentPriceCommandUseCase.registerWatchingStock(stockId, userId);
            log.debug("종목 실시간 감시 등록 - stockId: {}, userId: {}", stockId, userId);
          } catch (Exception ex) {
            log.error("종목 실시간 감시 등록 실패 - stockId: {}, userId: {}", stockId, userId, ex);
          }
        }
      }
      topicSubscribers.computeIfAbsent(topic, key -> ConcurrentHashMap.newKeySet()).add(connection.getSession().getId());
      subscribed.add(topic);
    }

    if (limitExceeded) {
      subscriptionsLimitExceededCounter.increment();
    }
    if (!subscribed.isEmpty()) {
      updateTopicSubscriberGauge();
    }
    return new SubscribeResult(subscribed, alreadySubscribed, rejected, limitExceeded);
  }

  public UnsubscribeResult unsubscribe(MarketWebSocketConnection connection, List<String> topics) {
    CustomWebSocketSession state = connection.getState();
    UUID userId = connection.getUserId();
    List<String> unsubscribed = new ArrayList<>();
    List<String> notSubscribed = new ArrayList<>();

    for (String topic : topics) {
      if (!state.getSubscribedTopics().contains(topic)) {
        notSubscribed.add(topic);
        continue;
      }
      state.getSubscribedTopics().remove(topic);
      unsubscribed.add(topic);

      Set<String> subscribers = topicSubscribers.get(topic);
      if (subscribers != null) {
        subscribers.remove(connection.getSession().getId());
        if (subscribers.isEmpty()) {
          topicSubscribers.remove(topic);
        }
      }
      if (userId != null) {
        Map<String, Integer> userTopics = userSubscriptions.get(userId);
        if (userTopics != null) {
          boolean wasLastSubscription = decrementUserTopic(userTopics, topic);
          // 해당 유저의 해당 종목 구독이 모두 해제된 경우
          if (wasLastSubscription) {
            Long stockId = extractStockId(topic);
            if (stockId != null) {
              try {
                currentPriceCommandUseCase.unregisterWatchingStock(stockId, userId);
                log.debug("종목 실시간 감시 해제 - stockId: {}, userId: {}", stockId, userId);
              } catch (Exception ex) {
                log.error("종목 실시간 감시 해제 실패 - stockId: {}, userId: {}", stockId, userId, ex);
              }
            }
          }
          if (userTopics.isEmpty()) {
            userSubscriptions.remove(userId);
          }
        }
      }
    }

    if (!unsubscribed.isEmpty()) {
      updateTopicSubscriberGauge();
    }
    return new UnsubscribeResult(unsubscribed, notSubscribed);
  }

    public List<MarketWebSocketConnection> getSubscribers(String topic) {
        Set<String> subscriberIds = topicSubscribers.get(topic);
        if (subscriberIds == null || subscriberIds.isEmpty()) {
            return List.of();
        }
        List<MarketWebSocketConnection> result = new ArrayList<>();
        for (String sessionId : subscriberIds) {
            MarketWebSocketConnection connection = connections.get(sessionId);
            if (connection != null) {
                result.add(connection);
            }
        }
        return result;
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

  private boolean decrementUserTopic(Map<String, Integer> userTopics, String topic) {
    Integer count = userTopics.get(topic);
    if (count == null) {
      return false;
    }
    if (count <= 1) {
      userTopics.remove(topic);
      return true; // 마지막 구독이 해제됨
    } else {
      userTopics.put(topic, count - 1);
      return false; // 아직 다른 세션에서 구독 중
    }
  }

  private void updateTopicSubscriberGauge() {
    topicSubscriberGauge.register(
        topicSubscribers.entrySet().stream()
            .map(e -> MultiGauge.Row.of(Tags.of("topic", e.getKey()), e.getValue(), s -> (double) s.size()))
            .collect(Collectors.toList()),
        true
    );
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
