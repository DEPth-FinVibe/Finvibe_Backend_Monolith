package depth.finvibe.modules.gamification.infra.client;

import depth.finvibe.modules.gamification.application.port.out.UserServiceClient;
import depth.finvibe.modules.user.application.port.in.UserQueryUseCase;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserServiceClientImpl implements UserServiceClient {

    private final UserQueryUseCase userQueryUseCase;

    @Override
    public Optional<String> getNickname(UUID userId) {
        try {
            return Optional.ofNullable(userQueryUseCase.getNickname(userId));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    @Override
    public Map<UUID, String> getNicknamesByIds(Collection<UUID> userIds) {
        List<UUID> ids = new ArrayList<>(userIds);
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<UUID, String> nicknames = new LinkedHashMap<>();
        for (UUID userId : ids) {
            getNickname(userId).ifPresent(nickname -> nicknames.put(userId, nickname));
        }
        return nicknames;
    }
}
