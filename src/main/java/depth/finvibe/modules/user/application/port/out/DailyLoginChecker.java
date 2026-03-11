package depth.finvibe.modules.user.application.port.out;

import java.util.UUID;

public interface DailyLoginChecker {
    boolean checkAndMarkDailyLogin(UUID userId);
}
