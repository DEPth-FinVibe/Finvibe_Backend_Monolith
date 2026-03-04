package depth.finvibe.modules.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.asset.domain.enums.PortfolioChartInterval;

public class PortfolioPerformanceDto {

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "PortfolioPerformancePoint", description = "포트폴리오 성과 차트 데이터 포인트")
  public static class Point {
    @Schema(description = "기간 시작일", example = "2026-02-01")
    private LocalDate periodStartDate;

    @Schema(description = "총 평가금액", example = "1234567.89")
    private BigDecimal totalCurrentValue;

    @Schema(description = "총 수익률(%)", example = "7.3200")
    private BigDecimal totalReturnRate;
  }

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "PortfolioPerformanceSeries", description = "포트폴리오별 성과 시리즈")
  public static class PortfolioSeries {
    @Schema(description = "포트폴리오 ID", example = "1")
    private Long portfolioId;

    @Schema(description = "포트폴리오 이름", example = "성장형")
    private String portfolioName;

    @Schema(description = "차트 포인트 목록")
    private List<Point> points;
  }

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "PortfolioPerformanceChartResponse", description = "포트폴리오 성과 차트 응답")
  public static class ChartResponse {
    @Schema(description = "집계 단위", example = "WEEKLY")
    private PortfolioChartInterval interval;

    @Schema(description = "조회 시작일", example = "2026-01-01")
    private LocalDate startDate;

    @Schema(description = "조회 종료일", example = "2026-02-10")
    private LocalDate endDate;

    @Schema(description = "포트폴리오별 시리즈")
    private List<PortfolioSeries> portfolios;

    @Schema(description = "전체 합산 시리즈")
    private List<Point> total;
  }
}
