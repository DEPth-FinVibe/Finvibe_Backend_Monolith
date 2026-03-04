package depth.finvibe.modules.gamification.api.external;

import java.util.List;

import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.gamification.application.port.in.BadgeQueryUseCase;
import depth.finvibe.modules.gamification.dto.BadgeDto;

@Tag(name = "배지", description = "배지 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/badges")
public class BadgeController {

    private final BadgeQueryUseCase badgeQueryUseCase;

    @Operation(summary = "전체 배지 목록 조회", description = "모든 배지의 통계 정보를 조회합니다")
    @GetMapping
    public List<BadgeDto.BadgeStatistics> getAllBadges() {
        return badgeQueryUseCase.getAllBadges();
    }

    @Operation(summary = "내 배지 목록 조회", description = "인증된 사용자의 획득 배지 목록을 반환합니다")
    @GetMapping("/me")
    public List<BadgeDto.BadgeInfo> getMyBadges(@AuthenticatedUser Requester requester) {
        return badgeQueryUseCase.getUserBadges(requester.getUuid());
    }
}
