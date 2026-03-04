package depth.finvibe.modules.market.infra.client.tokenmanage;

import java.time.Duration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KisTokenScheduler {

    private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(1);

    private final KisTokenManager tokenManager;
    private final TaskScheduler taskScheduler;

    @PostConstruct
    public void init() {
        tokenManager.refreshTokensForAllocatedCredentials();
        taskScheduler.scheduleAtFixedRate(tokenManager::refreshTokensForAllocatedCredentials, REFRESH_INTERVAL);
    }
}
