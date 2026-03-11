package depth.finvibe.boot.security.insight;

import depth.finvibe.modules.user.domain.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenVerifier {
    private static final String ALG_HS256 = "HS256";
    private static final String USER_UUID_CLAIM = "id";
    private static final String ROLE_CLAIM = "role";
    private static final String EXP_CLAIM = "exp";

    private final ObjectMapper objectMapper;

    @Value("${security.jwt.hmac-secret}")
    private String hmacSecret;

    public VerifiedToken verify(String token) {
        String[] chunks = token.split("\\.");
        if (chunks.length != 3) {
            throw new JwtVerificationException("Invalid token format.");
        }

        String headerJson = decode(chunks[0]);
        String payloadJson = decode(chunks[1]);

        Map<String, Object> header = readJson(headerJson);
        if (!ALG_HS256.equals(header.getOrDefault("alg", ""))) {
            throw new JwtVerificationException("Unsupported JWT algorithm.");
        }

        String data = chunks[0] + "." + chunks[1];
        String expectedSignature = sign(data);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), chunks[2].getBytes(StandardCharsets.UTF_8))) {
            throw new JwtVerificationException("Invalid JWT signature.");
        }

        Map<String, Object> claims = readJson(payloadJson);
        validateExpiration(claims.get(EXP_CLAIM));

        UUID userId = parseUuid(claims.get(USER_UUID_CLAIM));
        UserRole role = parseRole(claims.get(ROLE_CLAIM));
        if (userId == null) {
            throw new JwtVerificationException("Missing user id.");
        }
        return new VerifiedToken(userId, role);
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new JwtVerificationException("Invalid JWT payload.");
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception ex) {
            throw new JwtVerificationException("Failed to sign JWT.");
        }
    }

    private void validateExpiration(Object exp) {
        if (exp == null) {
            return;
        }
        long expValue = Long.parseLong(exp.toString());
        if (Instant.ofEpochSecond(expValue).isBefore(Instant.now())) {
            throw new JwtVerificationException("JWT expired.");
        }
    }

    private UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        return UUID.fromString(value.toString());
    }

    private UserRole parseRole(Object value) {
        if (value == null) {
            return null;
        }
        return UserRole.valueOf(value.toString());
    }

    public record VerifiedToken(UUID userId, UserRole role) {}

    public static class JwtVerificationException extends RuntimeException {
        public JwtVerificationException(String message) {
            super(message);
        }
    }
}
