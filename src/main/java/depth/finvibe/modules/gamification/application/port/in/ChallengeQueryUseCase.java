package depth.finvibe.modules.gamification.application.port.in;

import depth.finvibe.modules.gamification.dto.ChallengeDto;

import java.util.List;
import java.util.UUID;

public interface ChallengeQueryUseCase {
    /**
     * 사용자의 챌린지 목록을 조회합니다.
     * 진행 현황(Metric 값을 통해 산출)도 함께 포함됩니다.
     *
     * @param userId 사용자 ID
     * @return 챌린지 응답 목록
     */
    List<ChallengeDto.ChallengeResponse> getPersonalChallenges(UUID userId);

    /**
     * 사용자의 월별 챌린지 완료 내역을 조회합니다.
     *
     * @param userId 사용자 ID
     * @param year 조회 년도
     * @param month 조회 월 (1-12)
     * @return 챌린지 완료 내역 목록
     */
    List<ChallengeDto.ChallengeHistoryResponse> getCompletedChallenges(UUID userId, int year, int month);
}
