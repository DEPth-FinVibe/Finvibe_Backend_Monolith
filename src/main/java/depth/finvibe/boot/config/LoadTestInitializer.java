package depth.finvibe.boot.config;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.application.port.out.CurrentStockWatcherRepository;
import depth.finvibe.modules.market.application.port.out.MarketDataStreamPort;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.CurrentStockWatcher;
import depth.finvibe.modules.market.domain.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("loadtest")
@RequiredArgsConstructor
public class LoadTestInitializer implements ApplicationRunner {

	private static final UUID LOAD_TEST_WATCHER = UUID.fromString("00000000-0000-0000-0000-000000000000");

	private final StockRepository stockRepository;
	private final CurrentStockWatcherRepository watcherRepository;
	private final MarketDataStreamPort marketDataStreamPort;

	@Override
	public void run(ApplicationArguments args) {
		List<Stock> stocks = stockRepository.findAll();

		marketDataStreamPort.initializeSessions();
		for (Stock stock : stocks) {
			watcherRepository.save(CurrentStockWatcher.create(stock.getId(), LOAD_TEST_WATCHER));
			marketDataStreamPort.subscribe(stock.getId(), stock.getSymbol());
		}
		log.info("[LoadTest] {}개 종목 구독 완료 — emit-interval: 100ms, 초당 약 {}건 이벤트 예상",
				stocks.size(), stocks.size() * 10);
	}
}
