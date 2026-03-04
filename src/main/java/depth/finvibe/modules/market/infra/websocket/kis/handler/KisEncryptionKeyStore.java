package depth.finvibe.modules.market.infra.websocket.kis.handler;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class KisEncryptionKeyStore {
    private final Map<String, AesKeyIv> encryptionKeys = new ConcurrentHashMap<>();

    public void put(String trId, String key, String iv) {
        if (trId == null || trId.isBlank() || key == null || key.isBlank() || iv == null || iv.isBlank()) {
            return;
        }
        encryptionKeys.put(trId, new AesKeyIv(key, iv));
    }

    public Optional<AesKeyIv> find(String trId) {
        if (trId == null || trId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(encryptionKeys.get(trId));
    }

    public record AesKeyIv(String key, String iv) {}
}
