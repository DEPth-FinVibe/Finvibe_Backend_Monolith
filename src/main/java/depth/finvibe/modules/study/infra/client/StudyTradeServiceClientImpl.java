package depth.finvibe.modules.study.infra.client;

import depth.finvibe.modules.study.application.port.out.TradeServiceClient;
import depth.finvibe.common.gamification.dto.TradeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StudyTradeServiceClientImpl implements TradeServiceClient {
    private static final TypeReference<List<TradeDto.TradeHistoryResponse>> RESPONSE_TYPE = new TypeReference<>() {};

    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://investment")
            .build();
    private final ObjectMapper objectMapper;

    @Override
    public List<TradeDto.TradeHistoryResponse> getUserTradeHistories(String userId, LocalDate fromDate, LocalDate toDate) {
        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/trades/history")
                            .queryParam("fromDate", fromDate)
                            .queryParam("toDate", toDate)
                            .queryParam("userId", userId)
                            .build())
                    .retrieve()
                    .body(String.class);

            if (response == null) {
                return List.of();
            }
            return objectMapper.readValue(response, RESPONSE_TYPE);
        } catch (Exception ex) {
            log.error("Failed to fetch trade histories for userId {}: {}", userId, ex.getMessage());
            return List.of();
        }
    }
}
