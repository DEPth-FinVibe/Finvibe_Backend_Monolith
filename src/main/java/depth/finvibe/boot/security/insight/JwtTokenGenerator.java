package depth.finvibe.boot.security.insight;

import depth.finvibe.modules.user.domain.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenGenerator {
    private static final String ALG_HS256 = "HS256";
    private static final String TYP_JWT = "JWT";
    private static final String USER_UUID_CLAIM = "id";
    private static final String ROLE_CLAIM = "role";
    private static final String EXP_CLAIM = "exp";

    private final ObjectMapper objectMapper;

    @Value("${security.jwt.hmac-secret}")
    private String hmacSecret;

    /**
     * JWT 토큰을 생성합니다.
     *
     * @param userId 사용자 ID
     * @param role 사용자 역할
     * @param expirationSeconds 만료 시간 (초)
     * @return 생성된 JWT 토큰
     */
    public String generate(UUID userId, UserRole role, long expirationSeconds) {
        try {
            // Header 생성
            Map<String, Object> header = new HashMap<>();
            header.put("alg", ALG_HS256);
            header.put("typ", TYP_JWT);
            String encodedHeader = encode(objectMapper.writeValueAsString(header));

            // Payload 생성
            Map<String, Object> payload = new HashMap<>();
            payload.put(USER_UUID_CLAIM, userId.toString());
            if (role != null) {
                payload.put(ROLE_CLAIM, role.name());
            }
            payload.put(EXP_CLAIM, Instant.now().plusSeconds(expirationSeconds).getEpochSecond());
            String encodedPayload = encode(objectMapper.writeValueAsString(payload));

            // Signature 생성
            String data = encodedHeader + "." + encodedPayload;
            String signature = sign(data);

            return data + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    /**
     * 만료 시간 없는 JWT 토큰을 생성합니다.
     *
     * @param userId 사용자 ID
     * @param role 사용자 역할
     * @return 생성된 JWT 토큰
     */
    public String generateWithoutExpiration(UUID userId, UserRole role) {
        try {
            // Header 생성
            Map<String, Object> header = new HashMap<>();
            header.put("alg", ALG_HS256);
            header.put("typ", TYP_JWT);
            String encodedHeader = encode(objectMapper.writeValueAsString(header));

            // Payload 생성
            Map<String, Object> payload = new HashMap<>();
            payload.put(USER_UUID_CLAIM, userId.toString());
            if (role != null) {
                payload.put(ROLE_CLAIM, role.name());
            }
            String encodedPayload = encode(objectMapper.writeValueAsString(payload));

            // Signature 생성
            String data = encodedHeader + "." + encodedPayload;
            String signature = sign(data);

            return data + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to sign JWT", ex);
        }
    }
}
