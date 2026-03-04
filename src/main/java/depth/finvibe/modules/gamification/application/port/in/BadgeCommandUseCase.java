package depth.finvibe.modules.gamification.application.port.in;

import depth.finvibe.modules.gamification.domain.enums.Badge;

import java.util.UUID;

public interface BadgeCommandUseCase {
    /**
     * 사용자에게 특정 뱃지를 부여합니다.
     *
     * @param userId 사용자 ID
     * @param badge 부여할 뱃지 종류
     */
    void grantBadgeToUser(UUID userId, Badge badge);
}
