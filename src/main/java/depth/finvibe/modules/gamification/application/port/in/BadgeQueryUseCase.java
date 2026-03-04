package depth.finvibe.modules.gamification.application.port.in;

import java.util.List;
import java.util.UUID;

import depth.finvibe.modules.gamification.dto.BadgeDto;

public interface BadgeQueryUseCase {

    /**
     * 특정 사용자의 모든 뱃지 정보를 조회
     *
     * @param userId 사용자 ID
     * @return 사용자의 뱃지 목록
     */
    List<BadgeDto.BadgeInfo> getUserBadges(UUID userId);

    /**
     * 모든 뱃지 목록을 조회
     *
     * @return 모든 뱃지 정보 목록
     */
    List<BadgeDto.BadgeStatistics> getAllBadges();
}
