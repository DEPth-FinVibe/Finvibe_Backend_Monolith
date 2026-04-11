package depth.finvibe.modules.user.application.port.out;

import depth.finvibe.modules.user.domain.AuthTokenClaims;

public interface TokenResolver {
    boolean isTokenValid(String token);

    AuthTokenClaims parse(String token);
}
