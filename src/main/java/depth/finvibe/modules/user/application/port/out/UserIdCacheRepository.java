package depth.finvibe.modules.user.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface UserIdCacheRepository {
    Optional<Long> findInternalUserIdByExternalUserId(UUID externalUserId);

    void save(UUID externalUserId, Long internalUserId);
}
