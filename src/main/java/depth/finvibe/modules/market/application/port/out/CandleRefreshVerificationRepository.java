package depth.finvibe.modules.market.application.port.out;

import java.time.LocalDateTime;

public interface CandleRefreshVerificationRepository {

    boolean isVerified(Long stockId, LocalDateTime completedMinute);

    void markVerified(Long stockId, LocalDateTime completedMinute);
}

