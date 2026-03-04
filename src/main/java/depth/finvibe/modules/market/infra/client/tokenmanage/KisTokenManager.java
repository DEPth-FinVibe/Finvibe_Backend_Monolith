package depth.finvibe.modules.market.infra.client.tokenmanage;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.infra.client.KisCredentialAllocator;
import depth.finvibe.modules.market.infra.client.tokenmanage.repository.TokenRepository;
import depth.finvibe.modules.market.infra.config.KisCredentialsProperties.Credential;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisTokenManager {

    private static final ZoneId KIS_ZONE = ZoneId.of("Asia/Seoul");

    private final KisTokenClient tokenClient;
    private final TokenRepository tokenRepository;
    private final KisCredentialAllocator credentialAllocator;

    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public String getAccessToken(Credential credential) {
        CachedToken cached = tokenCache.get(credential.appKey());
        if (cached != null && !cached.isExpiringSoon()) {
            return cached.token();
        }

        CachedToken stored = readTokenFromRepository(credential.appKey());
        if (stored != null && !stored.isExpiringSoon()) {
            tokenCache.put(credential.appKey(), stored);
            return stored.token();
        }

        CachedToken refreshed = refreshToken(credential);
        return refreshed == null ? null : refreshed.token();
    }

    public void refreshTokensForAllocatedCredentials() {
        List<Credential> allocated = credentialAllocator.getAllocatedCredentials();
        for (Credential credential : allocated) {
            CachedToken cached = readTokenFromRepository(credential.appKey());
            if (cached == null || cached.isExpiringSoon()) {
                refreshToken(credential);
            } else {
                tokenCache.put(credential.appKey(), cached);
            }
        }
    }

    public void invalidateToken(String appKey) {
        // 1. 메모리 캐시 삭제
        tokenCache.remove(appKey);

        // 2. Redis 삭제
        tokenRepository.deleteToken(appKey);

        log.warn("KIS access token invalidated - appKey={}", maskAppKey(appKey));
    }

    private String maskAppKey(String appKey) {
        if (appKey == null || appKey.length() < 8) {
            return "***";
        }
        return appKey.substring(0, 4) + "****" + appKey.substring(appKey.length() - 4);
    }

    private CachedToken refreshToken(Credential credential) {
        if (!tokenRepository.acquireRefreshLock(credential.appKey())) {
            waitForSharedToken(credential.appKey());
            CachedToken cachedToken = readTokenFromRepository(credential.appKey());
            if (cachedToken != null) {
                tokenCache.put(credential.appKey(), cachedToken);
            }
            return cachedToken;
        }

        try {
            KisTokenClient.TokenResponse response = tokenClient.requestAccessToken(
                    credential.appKey(),
                    credential.appSecret()
            );
            if (response == null) {
                return null;
            }

            LocalDateTime expiresAt = LocalDateTime.now(KIS_ZONE).plusSeconds(response.expiresIn());
            CachedToken cachedToken = new CachedToken(response.accessToken(), expiresAt);
            tokenRepository.saveToken(credential.appKey(), response.accessToken(), expiresAt);
            tokenCache.put(credential.appKey(), cachedToken);
            return cachedToken;
        } finally {
            tokenRepository.releaseRefreshLock(credential.appKey());
        }
    }

    private CachedToken readTokenFromRepository(String appKey) {
        String token = tokenRepository.getAccessToken(appKey);
        LocalDateTime expiresAt = tokenRepository.getExpiresAt(appKey);
        if (token == null || expiresAt == null) {
            return null;
        }
        return new CachedToken(token, expiresAt);
    }

    private void waitForSharedToken(String appKey) {
        int attempts = 10;
        while (attempts-- > 0) {
            CachedToken cachedToken = readTokenFromRepository(appKey);
            if (cachedToken != null && !cachedToken.isExpiringSoon()) {
                tokenCache.put(appKey, cachedToken);
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public record CachedToken(String token, LocalDateTime expiresAt) {
        boolean isExpiringSoon() {
            return expiresAt.isBefore(LocalDateTime.now(KIS_ZONE).plusMinutes(10));
        }
    }
}
