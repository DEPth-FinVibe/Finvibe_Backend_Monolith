package depth.finvibe.modules.user.infra.security;

import depth.finvibe.modules.user.application.port.out.TemporaryTokenProvider;
import depth.finvibe.modules.user.application.port.out.TemporaryTokenResolver;
import depth.finvibe.modules.user.domain.enums.AuthProvider;
import depth.finvibe.modules.user.domain.vo.OAuthInfo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTemporaryTokenProvider implements TemporaryTokenProvider, TemporaryTokenResolver {
    private static final String CLAIM_PROVIDER = "provider";
    private static final String CLAIM_PROVIDER_ID = "provider_id";

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.access-token-expiration}")
    private long temporaryTokenExpiration;

    private SecretKey key;

    @PostConstruct
    protected void init() {
        key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generateTemporaryToken(AuthProvider provider, String providerId) {
        if (provider == null) {
            throw new IllegalArgumentException("AuthProvider must not be null");
        }
        Date now = new Date();
        Date expiry = new Date(now.getTime() + temporaryTokenExpiration);

        return Jwts.builder()
                .issuer(issuer)
                .claim(CLAIM_PROVIDER, provider.name())
                .claim(CLAIM_PROVIDER_ID, providerId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public OAuthInfo getOAuthInfoFromTemporaryToken(String temporaryToken) {
        Claims claims = parseClaims(temporaryToken);
        AuthProvider provider = AuthProvider.valueOf(claims.get(CLAIM_PROVIDER).toString());
        String providerId = claims.get(CLAIM_PROVIDER_ID).toString();
        return OAuthInfo.ofSocial(provider, providerId);
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

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
