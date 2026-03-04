package depth.finvibe.modules.user.application.port.out;

import depth.finvibe.modules.user.domain.vo.OAuthInfo;

public interface TemporaryTokenResolver {
    boolean isTokenValid(String token);

    OAuthInfo getOAuthInfoFromTemporaryToken(String temporaryToken);
}
