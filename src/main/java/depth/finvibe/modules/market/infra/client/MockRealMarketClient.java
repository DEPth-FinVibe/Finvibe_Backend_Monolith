package depth.finvibe.modules.market.infra.client;

import depth.finvibe.modules.market.application.port.out.RealMarketClient;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.domain.enums.RankType;
import depth.finvibe.modules.market.domain.enums.Timeframe;
import depth.finvibe.modules.market.domain.error.MarketErrorCode;
import depth.finvibe.modules.market.dto.PriceCandleDto;
import depth.finvibe.modules.market.dto.StockDto;
import depth.finvibe.common.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "market.provider", havingValue = "mock")
public class MockRealMarketClient implements RealMarketClient {

	private static final BigDecimal BASE_PRICE = BigDecimal.valueOf(50_000L);
	private static final BigDecimal BASE_VOLUME = BigDecimal.valueOf(10_000L);

	private final StockRepository stockRepository;

	@Override
	public List<StockDto.RealMarketStockResponse> fetchStocksInRealMarket() {
		return stockRepository.findAll().stream()
				.map(stock -> StockDto.RealMarketStockResponse.builder()
						.symbol(stock.getSymbol())
						.name(stock.getName())
						.typeCode(String.valueOf(stock.getCategoryId()))
						.build())
				.toList();
	}

	@Override
	public List<PriceCandleDto.Response> fetchPriceCandles(Long stockId, LocalDateTime startTime, LocalDateTime endTime, Timeframe timeframe) {
		Stock stock = stockRepository.findById(stockId)
				.orElseThrow(() -> new DomainException(MarketErrorCode.STOCK_NOT_FOUND));

		if (startTime == null || endTime == null || startTime.isAfter(endTime)) {
			return List.of();
		}

		List<PriceCandleDto.Response> responses = new ArrayList<>();
		LocalDateTime cursor = timeframe.normalizeStart(startTime);
		LocalDateTime end = timeframe.normalizeEnd(endTime);

		while (!cursor.isAfter(end)) {
			responses.add(buildCandle(stock, cursor, timeframe));
			cursor = timeframe.nextTime(cursor);
		}

		return responses;
	}

	@Override
	public List<StockDto.RankingResponse> fetchStockRankings() {
		List<Stock> stocks = stockRepository.findAll().stream()
				.sorted(Comparator.comparing(Stock::getSymbol))
				.limit(100)
				.toList();

		List<StockDto.RankingResponse> result = new ArrayList<>();
		appendRankings(result, stocks, RankType.TOP_VALUE);
		appendRankings(result, stocks, RankType.TOP_VOLUME);
		appendRankings(result, stocks, RankType.TOP_RISING);
		appendRankings(result, stocks, RankType.TOP_FALLING);
		return result;
	}

	@Override
	public List<PriceCandleDto.Response> bulkFetchCurrentPrices(List<String> stockSymbols) {
		if (stockSymbols == null || stockSymbols.isEmpty()) {
			return List.of();
		}

		Map<String, Stock> stockBySymbol = stockRepository.findAllBySymbolIn(stockSymbols).stream()
				.collect(Collectors.toMap(Stock::getSymbol, stock -> stock));

		return stockSymbols.stream()
				.map(stockBySymbol::get)
				.filter(java.util.Objects::nonNull)
				.map(stock -> buildCandle(stock, LocalDateTime.now().withSecond(0).withNano(0), Timeframe.MINUTE))
				.toList();
	}

	private void appendRankings(List<StockDto.RankingResponse> result, List<Stock> stocks, RankType rankType) {
		for (int i = 0; i < stocks.size(); i++) {
			result.add(StockDto.RankingResponse.builder()
					.symbol(stocks.get(i).getSymbol())
					.rankType(rankType)
					.rank(i + 1)
					.build());
		}
	}

	private PriceCandleDto.Response buildCandle(Stock stock, LocalDateTime at, Timeframe timeframe) {
		BigDecimal close = basePriceFor(stock).add(deltaFor(stock, at));
		BigDecimal spread = close.multiply(BigDecimal.valueOf(0.002)).setScale(0, RoundingMode.HALF_UP);
		BigDecimal open = close.subtract(spread);
		BigDecimal high = close.add(spread);
		BigDecimal low = close.subtract(spread.multiply(BigDecimal.valueOf(2)));
		BigDecimal volume = BASE_VOLUME.add(BigDecimal.valueOf(Math.abs(seed(stock.getSymbol())) % 5_000L));
		BigDecimal value = close.multiply(volume);
		BigDecimal changePct = BigDecimal.valueOf(((seed(stock.getSymbol()) % 600L) - 300L) / 100.0)
				.setScale(2, RoundingMode.HALF_UP);

		return PriceCandleDto.Response.builder()
				.open(open)
				.close(close)
				.high(high)
				.low(low)
				.volume(volume)
				.value(value)
				.stockId(stock.getId())
				.timeframe(timeframe)
				.at(at)
				.prevDayChangePct(changePct)
				.build();
	}

	private BigDecimal basePriceFor(Stock stock) {
		long seed = Math.abs(seed(stock.getSymbol()));
		return BASE_PRICE.add(BigDecimal.valueOf(seed % 25_000L));
	}

	private BigDecimal deltaFor(Stock stock, LocalDateTime at) {
		long seed = Math.abs(seed(stock.getSymbol()) + at.getMinute() + at.getHour() * 60L + at.getDayOfYear());
		long delta = (seed % 2_000L) - 1_000L;
		return BigDecimal.valueOf(delta);
	}

	private long seed(String symbol) {
		return symbol == null ? ThreadLocalRandom.current().nextLong(1_000L) : symbol.hashCode();
	}
}
