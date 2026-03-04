package depth.finvibe.modules.gamification.application.port.in;

import java.util.UUID;

import depth.finvibe.modules.gamification.dto.InternalGamificationDto;

public interface InternalGamificationQueryUseCase {

    InternalGamificationDto.UserSummaryResponse getUserSummary(UUID userId);
}
