package depth.finvibe.modules.user.infra.security;

import depth.finvibe.modules.user.application.port.out.TokenProvider;
import depth.finvibe.modules.user.application.port.out.TokenResolver;
import depth.finvibe.modules.user.domain.AuthTokenClaims;
import depth.finvibe.modules.user.domain.enums.AuthTokenType;
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
    private static final String CLAIM_TOKEN_FAMILY_ID = "tokenFamilyId";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

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
    public UserDto.TokenResponse generateToken(UUID userId, UserRole role, UUID tokenFamilyId) {
        Date now = new Date();
        Date accessExpiry = new Date(now.getTime() + accessTokenExpiration);
        Date refreshExpiry = new Date(now.getTime() + refreshTokenExpiration);

        String accessToken = createToken(userId, role, tokenFamilyId, AuthTokenType.ACCESS, accessExpiry);
        String refreshToken = createToken(userId, role, tokenFamilyId, AuthTokenType.REFRESH, refreshExpiry);

        return UserDto.TokenResponse.builder()
                .accessToken(accessToken)
                .accessExpiresAt(toOffsetDateTime(accessExpiry))
                .refreshToken(refreshToken)
                .refreshExpiresAt(toOffsetDateTime(refreshExpiry))
                .tokenFamilyId(tokenFamilyId)
                .build();
    }

    @Override
    public UserDto.TokenRefreshResponse refreshToken(UUID userId, UserRole role, UUID tokenFamilyId) {
        Date now = new Date();
        Date accessExpiry = new Date(now.getTime() + accessTokenExpiration);
        Date refreshExpiry = new Date(now.getTime() + refreshTokenExpiration);

        String newAccessToken = createToken(userId, role, tokenFamilyId, AuthTokenType.ACCESS, accessExpiry);
        String newRefreshToken = createToken(userId, role, tokenFamilyId, AuthTokenType.REFRESH, refreshExpiry);

        return UserDto.TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .accessExpiresAt(toOffsetDateTime(accessExpiry))
                .refreshToken(newRefreshToken)
                .refreshExpiresAt(toOffsetDateTime(refreshExpiry))
                .tokenFamilyId(tokenFamilyId)
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

    @Override
    public AuthTokenClaims parse(String token) {
        Claims claims = parseClaims(token);
        return new AuthTokenClaims(
                UUID.fromString(claims.getSubject()),
                parseRoleClaim(claims),
                parseTokenFamilyIdClaim(claims),
                AuthTokenType.valueOf(claims.get(CLAIM_TOKEN_TYPE, String.class)),
                UUID.fromString(claims.getId()),
                claims.getExpiration().toInstant()
        );
    }

    private String createToken(UUID userId, UserRole role, UUID tokenFamilyId, AuthTokenType tokenType, Date expiryDate) {
        Date now = new Date();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(issuer)
                .claim(CLAIM_ID, userId.toString())
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TOKEN_FAMILY_ID, tokenFamilyId.toString())
                .claim(CLAIM_TOKEN_TYPE, tokenType.name())
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

    /**
     * WS 인증용: 토큰 검증 후 userId 추출
     */
    public UUID getUserId(String token) {
        Claims claims = parseClaims(token);
        return UUID.fromString(claims.getSubject());
    }


    private UserRole parseRoleClaim(Claims claims) {
        Object value = claims.get(CLAIM_ROLE);
        if (value == null) {
            throw new IllegalArgumentException("Missing role claim in token");
        }
        return UserRole.valueOf(value.toString());
    }

    private UUID parseTokenFamilyIdClaim(Claims claims) {
        Object value = claims.get(CLAIM_TOKEN_FAMILY_ID);
        if (value == null) {
            return null;
        }
        return UUID.fromString(value.toString());
    }
}
