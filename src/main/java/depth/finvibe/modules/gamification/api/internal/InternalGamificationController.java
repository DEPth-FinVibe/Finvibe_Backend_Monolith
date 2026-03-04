package depth.finvibe.modules.gamification.api.internal;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import depth.finvibe.modules.gamification.application.port.in.InternalGamificationQueryUseCase;
import depth.finvibe.modules.gamification.dto.InternalGamificationDto;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/gamification")
public class InternalGamificationController {

    private final InternalGamificationQueryUseCase internalGamificationQueryUseCase;

    @GetMapping("/users/{userId}")
    public InternalGamificationDto.UserSummaryResponse getUserSummary(@PathVariable UUID userId) {
        return internalGamificationQueryUseCase.getUserSummary(userId);
    }
}
