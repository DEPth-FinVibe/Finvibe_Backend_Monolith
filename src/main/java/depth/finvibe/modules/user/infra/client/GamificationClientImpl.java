package depth.finvibe.modules.user.infra.client;

import depth.finvibe.modules.user.application.port.out.GamificationClient;
import depth.finvibe.modules.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GamificationClientImpl implements GamificationClient {

    private final HttpGamificationClient httpGamificationClient;

    @Override
    public Optional<UserDto.UserSummaryResponse> getUserSummary(UUID userId) {
        return Optional.ofNullable(httpGamificationClient.getUserSummary(userId));
    }
}
