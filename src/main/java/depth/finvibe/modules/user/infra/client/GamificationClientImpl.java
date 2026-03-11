package depth.finvibe.modules.user.infra.client;

import tools.jackson.databind.ObjectMapper;
import depth.finvibe.modules.gamification.application.port.in.InternalGamificationQueryUseCase;
import depth.finvibe.modules.gamification.dto.InternalGamificationDto;
import depth.finvibe.modules.user.application.port.out.GamificationClient;
import depth.finvibe.modules.user.dto.UserDto;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GamificationClientImpl implements GamificationClient {

    private final InternalGamificationQueryUseCase internalGamificationQueryUseCase;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<UserDto.UserSummaryResponse> getUserSummary(UUID userId) {
        InternalGamificationDto.UserSummaryResponse summary = internalGamificationQueryUseCase.getUserSummary(userId);
        return Optional.ofNullable(toUserSummaryResponse(summary));
    }

    private UserDto.UserSummaryResponse toUserSummaryResponse(InternalGamificationDto.UserSummaryResponse source) {
        return objectMapper.convertValue(source, UserDto.UserSummaryResponse.class);
    }
}
