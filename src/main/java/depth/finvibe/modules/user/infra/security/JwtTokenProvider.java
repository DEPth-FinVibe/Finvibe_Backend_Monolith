package depth.finvibe.modules.user.infra.security;

import depth.finvibe.modules.user.application.port.out.TokenProvider;
import depth.finvibe.modules.user.application.port.out.TokenResolver;
import depth.finvibe.modules.user.domain.enums.UserRole;
import depth.finvibe.modules.user.dto.UserDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider implements TokenProvider, TokenResolver {
    private static final String CLAIM_ID = "id";
    private static final String CLAIM_ROLE = "role";

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey key;

    @PostConstruct
    protected void init() {
        key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public UserDto.TokenResponse generateToken(UUID userId, UserRole role) {
        Date now = new Date();
        Date accessExpiry = new Date(now.getTime() + accessTokenExpiration);
        Date refreshExpiry = new Date(now.getTime() + refreshTokenExpiration);

        String accessToken = createToken(userId, role, accessExpiry);
        String refreshToken = createToken(userId, role, refreshExpiry);

        return UserDto.TokenResponse.builder()
                .accessToken(accessToken)
                .accessExpiresAt(toOffsetDateTime(accessExpiry))
                .refreshToken(refreshToken)
                .refreshExpiresAt(toOffsetDateTime(refreshExpiry))
                .build();
    }

    @Override
    public UserDto.TokenRefreshResponse refreshToken(String refreshToken) {
        Claims claims = parseClaims(refreshToken);
        UUID userId = UUID.fromString(claims.getSubject());
        UserRole role = parseRoleClaim(claims);

        Date now = new Date();
        Date accessExpiry = new Date(now.getTime() + accessTokenExpiration);
        Date refreshExpiry = new Date(now.getTime() + refreshTokenExpiration);

        String newAccessToken = createToken(userId, role, accessExpiry);
        String newRefreshToken = createToken(userId, role, refreshExpiry);

        return UserDto.TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .accessExpiresAt(toOffsetDateTime(accessExpiry))
                .refreshToken(newRefreshToken)
                .refreshExpiresAt(toOffsetDateTime(refreshExpiry))
                .build();
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String createToken(UUID userId, UserRole role, Date expiryDate) {
        Date now = new Date();

        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .claim(CLAIM_ID, userId.toString())
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private OffsetDateTime toOffsetDateTime(Date date) {
        return date.toInstant().atOffset(ZoneOffset.UTC);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private UserRole parseRoleClaim(Claims claims) {
        Object value = claims.get(CLAIM_ROLE);
        if (value == null) {
            throw new IllegalArgumentException("Missing role claim in token");
        }
        return UserRole.valueOf(value.toString());
    }
}
