package depth.finvibe.modules.asset.api.external;

import java.time.LocalDate;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.asset.application.port.in.AssetQueryUseCase;
import depth.finvibe.modules.asset.domain.enums.PortfolioChartInterval;
import depth.finvibe.modules.asset.dto.PortfolioPerformanceDto;
import depth.finvibe.modules.asset.dto.PortfolioGroupDto;

@RestController
@RequiredArgsConstructor
@Tag(name = "자산", description = "자산 API")
public class AssetController {

    private final AssetQueryUseCase queryUseCase;

    @GetMapping("/portfolios/{portfolioId}/assets")
    @Operation(summary = "포트폴리오 자산 조회", description = "포트폴리오에 속한 자산 목록을 조회합니다.")
    public ResponseEntity<List<PortfolioGroupDto.AssetResponse>> getAssetsByPortfolio(
            @Parameter(description = "포트폴리오 ID", example = "1") @PathVariable Long portfolioId,
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        return ResponseEntity.ok(queryUseCase.getAssetsByPortfolio(portfolioId, requester.getUuid()));
    }

    @GetMapping("/portfolios/comparison")
    @Operation(summary = "포트폴리오별 수익 비교 조회", description = "포트폴리오별 총 자산 금액, 수익률, 실현 수익을 조회합니다.")
    public ResponseEntity<List<PortfolioGroupDto.PortfolioComparisonResponse>> getPortfolioComparisons(
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        return ResponseEntity.ok(queryUseCase.getPortfolioComparisons(requester.getUuid()));
    }

    @GetMapping("/assets/allocation")
    @Operation(summary = "전체 자산 배분 조회", description = "사용자의 전체 자산 배분(현금/주식)과 기준금액 대비 증감 정보를 조회합니다.")
    public ResponseEntity<PortfolioGroupDto.AssetAllocationResponse> getAssetAllocation(
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        return ResponseEntity.ok(queryUseCase.getAssetAllocation(requester.getUuid()));
    }

    @GetMapping("/portfolios/performance-chart")
    @Operation(summary = "포트폴리오 성과 차트 조회", description = "포트폴리오별 평가금/수익률을 일별/주별/월별로 조회합니다.")
    public ResponseEntity<PortfolioPerformanceDto.ChartResponse> getPortfolioPerformanceChart(
            @Parameter(description = "조회 시작일", example = "2026-01-01") @RequestParam LocalDate startDate,
            @Parameter(description = "조회 종료일", example = "2026-02-10") @RequestParam LocalDate endDate,
            @Parameter(description = "차트 집계 단위", example = "WEEKLY") @RequestParam PortfolioChartInterval interval,
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        return ResponseEntity.ok(queryUseCase.getPortfolioPerformanceChart(requester.getUuid(), startDate, endDate, interval));
    }
}
