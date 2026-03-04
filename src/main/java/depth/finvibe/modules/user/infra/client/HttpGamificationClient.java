package depth.finvibe.modules.user.infra.client;

import depth.finvibe.modules.user.dto.UserDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.UUID;

@HttpExchange("/gamification/internal/gamification/users")
public interface HttpGamificationClient {
    @GetExchange("/{userId}")
    UserDto.UserSummaryResponse getUserSummary(@PathVariable UUID userId);
}
