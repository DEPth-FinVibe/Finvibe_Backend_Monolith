package depth.finvibe.modules.market.infra.client;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.application.port.out.StockMasterClient;
import depth.finvibe.modules.market.dto.StockDto.RealMarketStockResponse;

@Component
@RequiredArgsConstructor
public class KisStockMasterClient implements StockMasterClient {

	private final List<KisFileClient> kisFileClients;

	@Override
	public List<RealMarketStockResponse> fetchStocks() {
		List<CompletableFuture<List<RealMarketStockResponse>>> futures = kisFileClients.stream()
				.map(client -> CompletableFuture.supplyAsync(client::fetchStocksInKisFile))
				.toList();

		return futures.stream()
				.map(CompletableFuture::join)
				.flatMap(List::stream)
				.collect(Collectors.toList());
	}
}
