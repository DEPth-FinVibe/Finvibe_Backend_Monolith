package depth.finvibe.modules.study.infra.client;

import depth.finvibe.common.gamification.dto.TradeDto;
import depth.finvibe.modules.study.application.port.out.TradeServiceClient;
import depth.finvibe.modules.trade.application.port.in.TradeQueryUseCase;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class StudyTradeServiceClientImpl implements TradeServiceClient {

    private static final Logger log = LoggerFactory.getLogger(StudyTradeServiceClientImpl.class);
    private static final TypeReference<List<TradeDto.TradeHistoryResponse>> RESPONSE_TYPE = new TypeReference<>() {};

    private final TradeQueryUseCase tradeQueryUseCase;
    private final ObjectMapper objectMapper;

    @Override
    public List<TradeDto.TradeHistoryResponse> getUserTradeHistories(String userId, LocalDate fromDate, LocalDate toDate) {
        try {
            UUID parsedUserId = UUID.fromString(userId);
            return objectMapper.convertValue(
                    tradeQueryUseCase.findTradesByDateRange(parsedUserId, fromDate, toDate),
                    RESPONSE_TYPE
            );
        } catch (Exception exception) {
            log.error("Failed to fetch trade histories for userId {}: {}", userId, exception.getMessage());
            return List.of();
        }
    }
}
