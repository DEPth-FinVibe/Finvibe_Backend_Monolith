package depth.finvibe.modules.gamification.api.external;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.user.domain.enums.UserRole;
import depth.finvibe.modules.gamification.domain.enums.RankingPeriod;
import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.modules.gamification.application.port.in.XpQueryUseCase;
import depth.finvibe.modules.gamification.application.port.in.XpCommandUseCase;
import depth.finvibe.modules.gamification.dto.XpDto;
import depth.finvibe.common.error.DomainException;

@Tag(name = "경험치", description = "경험치 및 랭킹 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/xp")
public class XpController {

    private final XpCommandUseCase xpCommandUseCase;
    private final XpQueryUseCase xpQueryUseCase;

    @Operation(summary = "관리자 수동 XP 지급", description = "관리자가 특정 사용자에게 XP를 수동 지급합니다", hidden = true)
    @PostMapping("/admin/users/{userId}/grant")
    public void grantUserXpByAdmin(
            @PathVariable UUID userId,
            @RequestBody XpDto.GrantXpRequest request,
            @AuthenticatedUser Requester requester) {
        validateAdmin(requester);
        xpCommandUseCase.grantUserXp(userId, request.getValue(), request.getReason());
    }

    @Operation(summary = "내 경험치 조회", description = "인증된 사용자의 경험치 정보를 반환합니다")
    @GetMapping("/me")
    public XpDto.Response getMyXp(@AuthenticatedUser Requester requester) {
        return xpQueryUseCase.getUserXp(requester.getUuid());
    }

    @Operation(summary = "스쿼드 경험치 랭킹 조회", description = "스쿼드별 경험치 랭킹을 조회합니다")
    @GetMapping("/squads/ranking")
    public List<XpDto.SquadRankingResponse> getSquadXpRanking() {
        return xpQueryUseCase.getSquadXpRanking();
    }

    @Operation(summary = "내 스쿼드 기여도 랭킹 조회", description = "사용자 소속 스쿼드의 기여도 랭킹을 조회합니다")
    @GetMapping("/squads/contributions/me")
    public List<XpDto.ContributionRankingResponse> getMySquadContributionRanking(
            @AuthenticatedUser Requester requester) {
        return xpQueryUseCase.getSquadContributionRanking(requester.getUuid());
    }

    @Operation(summary = "전체 사용자 XP 랭킹 조회", description = "지정된 기간 동안 획득한 XP 합산 기준 전체 사용자 Top 100 랭킹을 조회합니다")
    @GetMapping("/users/ranking")
    public List<XpDto.UserRankingResponse> getUserXpRanking(
            @Parameter(description = "랭킹 기간 (DAILY, WEEKLY, MONTHLY)", example = "MONTHLY")
            @RequestParam(defaultValue = "MONTHLY") RankingPeriod period) {
        return xpQueryUseCase.getUserXpRanking(period);
    }

    private void validateAdmin(Requester requester) {
        if (requester.getRole() != UserRole.ADMIN) {
            throw new DomainException(GamificationErrorCode.FORBIDDEN_ACCESS);
        }
    }
}
