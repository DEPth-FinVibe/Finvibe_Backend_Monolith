package depth.finvibe.modules.market.infra.client.tokenmanage.repository;

import java.time.LocalDateTime;

public interface TokenRepository {
    String getAccessToken(String appKey);

    LocalDateTime getExpiresAt(String appKey);

    void saveToken(String appKey, String token, LocalDateTime expiresAt);

    void deleteToken(String appKey);

    boolean acquireRefreshLock(String appKey);

    void releaseRefreshLock(String appKey);
}
