package depth.finvibe.modules.gamification.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface UserServiceClient {
    Optional<String> getNickname(Long userId);

    Map<Long, String> getNicknamesByIds(Collection<Long> userIds);
}
