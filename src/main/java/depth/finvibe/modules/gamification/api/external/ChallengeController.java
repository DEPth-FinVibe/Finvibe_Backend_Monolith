package depth.finvibe.modules.gamification.api.external;

import java.util.List;

import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.gamification.application.port.in.ChallengeQueryUseCase;
import depth.finvibe.modules.gamification.dto.ChallengeDto;

@Tag(name = "챌린지", description = "개인 챌린지 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/challenges")
public class ChallengeController {

    private final ChallengeQueryUseCase challengeQueryUseCase;

    @Operation(summary = "내 챌린지 목록 조회", description = "인증된 사용자의 개인 챌린지 목록을 반환합니다")
    @GetMapping("/me")
    public List<ChallengeDto.ChallengeResponse> getMyChallenges(@AuthenticatedUser Requester requester) {
        return challengeQueryUseCase.getPersonalChallenges(requester.getUuid());
    }

    @Operation(summary = "월별 챌린지 완료 내역 조회", description = "인증된 사용자의 특정 월의 챌린지 완료 내역을 반환합니다")
    @GetMapping("/completed")
    public List<ChallengeDto.ChallengeHistoryResponse> getCompletedChallenges(
            @AuthenticatedUser Requester requester,
            @Parameter(description = "조회 년도", example = "2025") @RequestParam int year,
            @Parameter(description = "조회 월 (1-12)", example = "1") @RequestParam int month) {
        return challengeQueryUseCase.getCompletedChallenges(requester.getUuid(), year, month);
    }
}
