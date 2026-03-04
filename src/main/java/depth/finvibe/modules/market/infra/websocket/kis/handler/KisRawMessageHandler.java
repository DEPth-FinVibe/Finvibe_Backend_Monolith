package depth.finvibe.modules.market.infra.websocket.kis.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import depth.finvibe.modules.market.infra.websocket.kis.handler.parser.KisDataMessageParser;
import depth.finvibe.modules.market.infra.websocket.kis.handler.parser.KisJsonMessageParser;

public class KisRawMessageHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(KisRawMessageHandler.class);
    private final KisMessageHandler kisMessageHandler;
    private final KisEncryptionKeyStore encryptionKeyStore = new KisEncryptionKeyStore();
    private final KisJsonMessageParser jsonMessageHandler;
    private final KisDataMessageParser dataMessageHandler;

    public KisRawMessageHandler(KisMessageHandler kisMessageHandler, ObjectMapper objectMapper) {
        this.kisMessageHandler = kisMessageHandler;
        this.jsonMessageHandler = new KisJsonMessageParser(objectMapper, encryptionKeyStore, kisMessageHandler);
        this.dataMessageHandler = new KisDataMessageParser(encryptionKeyStore, kisMessageHandler);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            if (payload == null || payload.isBlank()) {
                return;
            }
            String trimmed = payload.trim();
            if (trimmed.startsWith("{")) {
                jsonMessageHandler.handle(session, message, trimmed);
                return;
            }
            dataMessageHandler.handle(trimmed);
        } catch (Exception ex) {
            kisMessageHandler.handleError(ex);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        kisMessageHandler.handleError(exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        kisMessageHandler.handleDisconnect();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        kisMessageHandler.handleConnect();
    }
}
