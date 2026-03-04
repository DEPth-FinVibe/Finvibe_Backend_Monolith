package depth.finvibe.modules.user.infra.client;

import depth.finvibe.modules.user.application.port.out.MarketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketClientImpl implements MarketClient {
    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://investment")
            .build();

    @Override
    public Optional<String> getStockNameByStockId(Long stockId) {
        try {
            String response = restClient.get()
                    .uri("/internal/market/stocks/{stockId}/name", stockId)
                    .retrieve()
                    .body(String.class);

            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.error("Failed to fetch stock name for stockId {}: {}", stockId, e.getMessage());
            return Optional.empty();
        }
    }
}
