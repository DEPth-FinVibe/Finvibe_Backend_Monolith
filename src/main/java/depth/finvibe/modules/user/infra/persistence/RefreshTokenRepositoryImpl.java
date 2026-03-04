package depth.finvibe.modules.user.infra.persistence;

import depth.finvibe.modules.user.application.port.out.RefreshTokenRepository;
import depth.finvibe.modules.user.domain.RefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {

    private final StringRedisTemplate redisTemplate;
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String USER_TOKEN_PREFIX = "user_token:";
    private static final long REFRESH_TOKEN_TTL = 14; // 14 days

    @Override
    public void save(RefreshToken refreshToken) {
        String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken.getToken();
        String userKey = USER_TOKEN_PREFIX + refreshToken.getUserId();
        
        // 이전 토큰 삭제 (Single Session 보장)
        String oldToken = redisTemplate.opsForValue().get(userKey);
        if (oldToken != null) {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + oldToken);
        }
        
        redisTemplate.opsForValue().set(tokenKey, refreshToken.getUserId().toString(), REFRESH_TOKEN_TTL, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(userKey, refreshToken.getToken(), REFRESH_TOKEN_TTL, TimeUnit.DAYS);
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        String userIdStr = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + token);
        if (userIdStr == null) {
            return Optional.empty();
        }
        
        return Optional.of(RefreshToken.create(UUID.fromString(userIdStr), token));
    }

    @Override
    public void deleteByUserId(UUID userId) {
        String userKey = USER_TOKEN_PREFIX + userId;
        String token = redisTemplate.opsForValue().get(userKey);
        if (token != null) {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + token);
            redisTemplate.delete(userKey);
        }
    }
}
