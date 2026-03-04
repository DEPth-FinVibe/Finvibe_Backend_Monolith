package depth.finvibe.modules.market.infra.websocket.kis.handler.parser;

import depth.finvibe.modules.market.infra.websocket.kis.handler.KisEncryptionKeyStore;
import depth.finvibe.modules.market.infra.websocket.kis.handler.KisMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RequiredArgsConstructor
public class KisJsonMessageParser {
    private final ObjectMapper objectMapper;
    private final KisEncryptionKeyStore encryptionKeyStore;
    private final KisMessageHandler messageHandler;

    public void handle(WebSocketSession session, TextMessage message, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String trId = root.path("header").path("tr_id").asText("");

            if ("PINGPONG".equals(trId)) {
                session.sendMessage(message);
                return;
            }

            String msg1 = root.path("body").path("msg1").asText("");
            if (msg1.contains("SUBSCRIBE")) {
                JsonNode output = root.path("body").path("output");
                String key = output.path("key").asText("");
                String iv = output.path("iv").asText("");
                encryptionKeyStore.put(trId, key, iv);
                return;
            }

            log.debug("Unhandled KIS JSON message. tr_id={}", trId);
        } catch (Exception ex) {
            log.warn("Failed to parse KIS JSON message.", ex);
            messageHandler.handleError(ex);
        }
    }
}
