package depth.finvibe.modules.gamification.application.port.in;

import depth.finvibe.modules.gamification.dto.XpDto;

import java.util.List;
import java.util.UUID;

public interface XpQueryUseCase {
    /**
     * 사용자의 현재 XP 정보를 조회합니다.
     *
     * @param userId 사용자 ID
     * @return XP 정보 응답 DTO
     */
    XpDto.Response getUserXp(UUID userId);

    /**
     * 스쿼드 XP 랭킹을 조회합니다.
     * 대학이 가지고 있는 총 XP의 합을 기준으로 랭킹 산정.
     *
     * @return 스쿼드 랭킹 목록
     */
    List<XpDto.SquadRankingResponse> getSquadXpRanking();

    /**
     * 우리 학교(스쿼드) 기여도 랭킹을 조회합니다.
     * 학교 구성원 중 가장 많은 XP를 얻은 사람을 찾아서 랭킹 산정.
     *
     * @param userId 요청한 사용자 ID (사용자가 속한 스쿼드 기준)
     * @return 기여도 랭킹 목록
     */
    List<XpDto.ContributionRankingResponse> getSquadContributionRanking(UUID userId);

    /**
     * 전체 사용자 XP 랭킹을 조회합니다.
     * 지정된 기간 동안 획득한 XP 합산 기준 Top 100을 반환합니다.
     *
     * @param period 랭킹 기간 (DAILY, WEEKLY, MONTHLY)
     * @return 사용자 XP 랭킹 목록
     */
    List<XpDto.UserRankingResponse> getUserXpRanking(depth.finvibe.modules.gamification.domain.enums.RankingPeriod period);
}
