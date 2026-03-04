package depth.finvibe.modules.market.infra.websocket.kis;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import tools.jackson.databind.ObjectMapper;

import depth.finvibe.modules.market.infra.websocket.kis.handler.AbstractKisMessageHandler;
import depth.finvibe.modules.market.infra.websocket.kis.handler.KisRawMessageHandler;
import depth.finvibe.modules.market.infra.websocket.kis.model.KisMessage;

@Slf4j
@RequiredArgsConstructor
public class KisWebsocketSession extends AbstractKisMessageHandler {
    private final Integer MAX_SUBSCRIPTIONS = 41;

    private final String approvalKey;
    private final Consumer<KisMessage.TransactionResponse> responseConsumer;
    private final ObjectMapper objectMapper;

    private WebSocketSession session;
    private AtomicBoolean connecting = new AtomicBoolean(false);

    private final List<String> subscribedSymbols = new CopyOnWriteArrayList<>();

    public CompletableFuture<KisWebsocketSession> connect(
            String uri
    ) {
        if (getIsConnected()) {
            throw new IllegalStateException("WebSocket already connected");
        }
        if (!connecting.compareAndSet(false, true)) {
            throw new IllegalStateException("WebSocket connection is already in progress");
        }

        WebSocketClient client = new StandardWebSocketClient();
        CompletableFuture<WebSocketSession> future = client.execute(new KisRawMessageHandler(this, objectMapper), uri);

        return future.thenApply(webSocketSession -> {
            this.session = webSocketSession;
            return this;
        });
    }

    public void subscribe(String symbol) {
        if (subscribedSymbols.size() >= MAX_SUBSCRIPTIONS) {
            throw new IllegalStateException("KIS WebSocket subscription limit exceeded");
        }

        KisMessage.TransactionRequest request = KisMessage.TransactionRequest.of(KisMessage.TransactionType.Subscribe, symbol);
        sendRequest(request);
        subscribedSymbols.add(symbol);
    }

  public void unsubscribe(String symbol) {
    if (!subscribedSymbols.contains(symbol)) {
      throw new IllegalStateException("Symbol not subscribed: " + symbol);
    }

    if (!getIsConnected()) {
      log.warn("WebSocket 세션이 닫혀있어 구독 해제 요청을 건너뜁니다 - symbol: {}", symbol);
      subscribedSymbols.remove(symbol);
      return;
    }

    KisMessage.TransactionRequest request = KisMessage.TransactionRequest.of(KisMessage.TransactionType.Unsubscribe, symbol);
    sendRequest(request);
    subscribedSymbols.remove(symbol);
  }

  public boolean close() {
    if (session == null) {
      subscribedSymbols.clear();
      return false;
    }

    try {
      if (session.isOpen()) {
        session.close();
      }
      return true;
    } catch (IOException ex) {
      log.warn("KIS WebSocket 세션 종료 실패", ex);
      return false;
    } finally {
      subscribedSymbols.clear();
    }
  }

    public Integer getSubscriptionCount() {
        return subscribedSymbols.size();
    }

    public List<String> getSubscribedSymbols() {
        return List.copyOf(subscribedSymbols);
    }

    private void sendRequest(KisMessage.TransactionRequest request) {
        KisMessage.RawTransactionRequest rawRequest = request.toRawRequest(approvalKey);
        String payload = objectMapper.writeValueAsString(rawRequest);

        try {
            session.sendMessage(new TextMessage(payload));
        } catch (IOException e) {
            log.error("Failed to send KIS WebSocket message", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void handleResponse(KisMessage.TransactionResponse response) {
        responseConsumer.accept(response);
    }

    public Boolean getIsConnected() {
        return session != null && session.isOpen();
    }
}
