package depth.finvibe.modules.asset.api.external;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import depth.finvibe.modules.asset.application.port.in.UserProfitRankingQueryUseCase;
import depth.finvibe.modules.asset.domain.enums.UserProfitRankType;
import depth.finvibe.modules.asset.dto.UserProfitRankingDto;

@RestController
@RequestMapping("/rankings")
@RequiredArgsConstructor
@Tag(name = "랭킹", description = "랭킹 API")
public class UserProfitRankingController {
  private static final int MAX_PAGE_SIZE = 100;
  private static final int MAX_PAGE_NUMBER = 1_000;

  private final UserProfitRankingQueryUseCase userProfitRankingQueryUseCase;

  @GetMapping("/user-profit")
  @Operation(summary = "사용자 수익률 랭킹 조회", description = "주간/월간 사용자 수익률 랭킹을 조회합니다.")
  public ResponseEntity<UserProfitRankingDto.RankingPageResponse> getUserProfitRankings(
    @Parameter(description = "랭킹 타입", example = "WEEKLY")
    @RequestParam UserProfitRankType type,
    @Parameter(description = "페이지 번호", example = "0")
    @RequestParam(defaultValue = "0") int page,
    @Parameter(description = "페이지 크기", example = "50")
    @RequestParam(defaultValue = "50") int size
  ) {
    int normalizedPage = Math.max(0, Math.min(page, MAX_PAGE_NUMBER));
    int normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    Pageable pageable = PageRequest.of(normalizedPage, normalizedSize);
    return ResponseEntity.ok(userProfitRankingQueryUseCase.getUserProfitRankings(type, pageable));
  }
}
