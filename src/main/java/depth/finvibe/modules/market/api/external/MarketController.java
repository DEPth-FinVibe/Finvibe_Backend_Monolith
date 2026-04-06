package depth.finvibe.modules.market.api.external;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import depth.finvibe.modules.market.application.port.in.CategoryQueryUseCase;
import depth.finvibe.modules.market.application.port.in.MarketQueryUseCase;
import depth.finvibe.modules.market.application.port.in.MarketStatusQueryUseCase;
import depth.finvibe.modules.market.domain.MarketHours;
import depth.finvibe.modules.market.domain.enums.MarketIndexType;
import depth.finvibe.modules.market.domain.enums.MarketStatus;
import depth.finvibe.modules.market.domain.enums.Timeframe;
import depth.finvibe.modules.market.domain.error.MarketErrorCode;
import depth.finvibe.modules.market.dto.CategoryDto;
import depth.finvibe.modules.market.dto.ClosingPriceDto;
import depth.finvibe.modules.market.dto.MarketStatusDto;
import depth.finvibe.modules.market.dto.PriceCandleDto;
import depth.finvibe.modules.market.dto.StockDto;
import depth.finvibe.common.error.DomainException;
@RestController
@RequiredArgsConstructor
@RequestMapping("/market")
@Tag(name = "시장", description = "시장 API")
public class MarketController {

    private final MarketQueryUseCase marketQueryUseCase;
    private final MarketStatusQueryUseCase marketStatusQueryUseCase;
    private final CategoryQueryUseCase categoryQueryUseCase;

    @GetMapping("/stocks/{stockId}/candles")
    @Operation(summary = "종목 캔들 조회", description = "종목의 캔들 데이터를 조회합니다.")
    public ResponseEntity<List<PriceCandleDto.Response>> getStockCandles(
            @Parameter(description = "종목 ID", example = "1") @PathVariable Long stockId,
            @Parameter(description = "시작 시각", example = "2024-01-01T09:00:00") @RequestParam LocalDateTime startTime,
            @Parameter(description = "종료 시각", example = "2024-01-02T15:30:00") @RequestParam LocalDateTime endTime,
            @Parameter(description = "타임프레임", example = "DAY") @RequestParam Timeframe timeframe
    ) {
        // 시작 시각이 종료 시각보다 이후인지 검증
        if (startTime.isAfter(endTime)) {
            throw new DomainException(MarketErrorCode.INVALID_START_END_TIME);
        }
        
        // 종료 시각이 완료된 마지막 캔들을 넘어가면 마지막 완료 캔들 시각으로 보정한다.
        // DAY/WEEK/MONTH/YEAR는 캔들 시작 시각 기준으로 보정해야 한다.
        LocalDateTime normalizedEndTime = timeframe.normalizeStart(endTime);
        LocalDateTime effectiveEndTime = resolveEffectiveEndTime(timeframe, normalizedEndTime);

        LocalDateTime normalizedStartTime = timeframe.normalizeStart(startTime);
        if (normalizedStartTime.isAfter(effectiveEndTime)) {
            throw new DomainException(MarketErrorCode.INVALID_TIME_RANGE);
        }
        
        List<PriceCandleDto.Response> candles = marketQueryUseCase.getStockCandles(
                stockId, startTime, effectiveEndTime, timeframe
        );
        return ResponseEntity.ok(candles);
    }

    @GetMapping("/stocks/{stockId}")
    @Operation(summary = "종목 단건 조회", description = "종목 ID로 종목 정보를 조회합니다.")
    public ResponseEntity<StockDto.Response> getStockById(
            @Parameter(description = "종목 ID", example = "1") @PathVariable Long stockId
    ) {
        return ResponseEntity.ok(marketQueryUseCase.getStockById(stockId));
    }

    @GetMapping("/stocks/{stockId}/current-price")
    @Operation(summary = "종목 현재가 조회", description = "종목 ID로 현재가를 조회합니다. 현재가 캐시에 값이 있으면 캐시를 사용합니다.")
    public ResponseEntity<Long> getCurrentPrice(
            @Parameter(description = "종목 ID", example = "1") @PathVariable Long stockId
    ) {
        return ResponseEntity.ok(marketQueryUseCase.getStockPriceInternal(stockId));
    }

    @GetMapping("/indexes/{indexType}/candles")
    @Operation(summary = "지수 캔들 조회", description = "코스피/코스닥 지수 캔들 데이터를 캐시에서 조회합니다.")
    public ResponseEntity<List<PriceCandleDto.Response>> getIndexCandles(
            @Parameter(description = "지수 타입", example = "KOSPI") @PathVariable MarketIndexType indexType,
            @Parameter(description = "시작 시각", example = "2024-01-01T09:00:00") @RequestParam LocalDateTime startTime,
            @Parameter(description = "종료 시각", example = "2024-01-01T15:30:00") @RequestParam LocalDateTime endTime
    ) {
        if (startTime.isAfter(endTime)) {
            throw new DomainException(MarketErrorCode.INVALID_START_END_TIME);
        }

        LocalDateTime lastCompletedMinute = Timeframe.MINUTE.lastCompletedTime(LocalDateTime.now());
        LocalDateTime effectiveEndTime = endTime.isAfter(lastCompletedMinute)
                ? lastCompletedMinute
                : endTime;

        if (startTime.isAfter(effectiveEndTime)) {
            throw new DomainException(MarketErrorCode.INVALID_TIME_RANGE);
        }

        List<PriceCandleDto.Response> candles = marketQueryUseCase.getIndexCandles(indexType, startTime, effectiveEndTime);
        return ResponseEntity.ok(candles);
    }
    
    private LocalDateTime resolveEffectiveEndTime(Timeframe timeframe, LocalDateTime normalizedEndTime) {
        LocalDateTime nowInKst = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        if (timeframe == Timeframe.MINUTE && MarketHours.getCurrentStatus() == MarketStatus.OPEN) {
            LocalDateTime oneMinuteAgo = nowInKst.minusMinutes(1).withSecond(0).withNano(0);
            return normalizedEndTime.isAfter(oneMinuteAgo) ? oneMinuteAgo : normalizedEndTime;
        }

        LocalDateTime lastCompletedCandleTime = timeframe.lastCompletedTime(nowInKst);
        return normalizedEndTime.isAfter(lastCompletedCandleTime)
                ? lastCompletedCandleTime
                : normalizedEndTime;
    }

    @GetMapping("/stocks/closing-prices")
    @Operation(summary = "종가 조회", description = "종목의 최신 종가를 조회합니다.")
    public ResponseEntity<List<ClosingPriceDto.Response>> getClosingPrices(
            @Parameter(description = "종목 ID 목록", example = "1,2,3") @RequestParam List<Long> stockIds
    ) {
        List<ClosingPriceDto.Response> closingPrices = marketQueryUseCase.getClosingPrices(stockIds);
        return ResponseEntity.ok(closingPrices);
    }

    @GetMapping("/stocks/top-by-value")
    @Operation(summary = "거래대금 TOP 조회", description = "거래대금 상위 종목을 조회합니다.")
    public ResponseEntity<List<StockDto.Response>> getTopStocksByValue() {
        List<StockDto.Response> stocks = marketQueryUseCase.getTopStocksByValue();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/stocks/top-by-volume")
    @Operation(summary = "거래량 TOP 조회", description = "거래량 상위 종목을 조회합니다.")
    public ResponseEntity<List<StockDto.Response>> getTopStocksByVolume() {
        List<StockDto.Response> stocks = marketQueryUseCase.getTopStocksByVolume();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/stocks/top-rising")
    @Operation(summary = "상승 TOP 조회", description = "상승률 상위 종목을 조회합니다.")
    public ResponseEntity<List<StockDto.Response>> getTopRisingStocks() {
        List<StockDto.Response> stocks = marketQueryUseCase.getTopRisingStocks();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/stocks/top-falling")
    @Operation(summary = "하락 TOP 조회", description = "하락률 상위 종목을 조회합니다.")
    public ResponseEntity<List<StockDto.Response>> getTopFallingStocks() {
        List<StockDto.Response> stocks = marketQueryUseCase.getTopFallingStocks();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/stocks/search")
    @Operation(summary = "종목 검색", description = "검색어로 종목을 조회합니다.")
    public ResponseEntity<List<StockDto.Response>> searchStocks(
            @Parameter(description = "검색어", example = "삼성") @RequestParam(defaultValue = "") String query
    ) {
        List<StockDto.Response> stocks = marketQueryUseCase.searchStocks(query);
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/status")
    @Operation(summary = "장 상태 조회", description = "시장 장 상태를 조회합니다.")
    public ResponseEntity<MarketStatusDto.Response> getMarketStatus() {
        return ResponseEntity.ok(marketStatusQueryUseCase.getMarketStatus());
    }

    @GetMapping("/categories")
    @Operation(summary = "카테고리 목록 조회", description = "전체 카테고리 목록을 조회합니다.")
    public ResponseEntity<List<CategoryDto.Response>> getCategories() {
        return ResponseEntity.ok(categoryQueryUseCase.getAllCategories());
    }

    @GetMapping("/categories/{categoryId}/change-rate")
    @Operation(summary = "카테고리 등락률 조회", description = "카테고리별 평균 등락률을 조회합니다.")
    public ResponseEntity<CategoryDto.ChangeRateResponse> getCategoryChangeRate(
            @Parameter(description = "카테고리 ID", example = "1") @PathVariable Long categoryId
    ) {
        return ResponseEntity.ok(categoryQueryUseCase.getCategoryChangeRate(categoryId));
    }

    @GetMapping("/categories/{categoryId}/stocks")
    @Operation(summary = "카테고리 종목 거래대금순 조회", description = "카테고리별 종목을 거래대금순으로 조회합니다.")
    public ResponseEntity<CategoryDto.StockListResponse> getCategoryStocksByValue(
            @Parameter(description = "카테고리 ID", example = "1") @PathVariable Long categoryId
    ) {
        return ResponseEntity.ok(categoryQueryUseCase.getCategoryStocksByValue(categoryId));
    }
}
