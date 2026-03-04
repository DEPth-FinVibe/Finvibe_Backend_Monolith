package depth.finvibe.modules.user.application.port.out;

import depth.finvibe.modules.user.dto.UserDto;

import java.util.Optional;
import java.util.UUID;

public interface GamificationClient {
    Optional<UserDto.UserSummaryResponse> getUserSummary(UUID userId);
}
