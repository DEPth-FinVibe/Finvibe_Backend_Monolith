package depth.finvibe.modules.user.application.port.out;

import depth.finvibe.modules.user.domain.enums.AuthProvider;

public interface TemporaryTokenProvider {
    String generateTemporaryToken(AuthProvider provider, String providerId);
}
