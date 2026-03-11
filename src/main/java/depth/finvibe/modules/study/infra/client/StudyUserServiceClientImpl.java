package depth.finvibe.modules.study.infra.client;

import depth.finvibe.modules.study.application.port.out.UserServiceClient;
import depth.finvibe.modules.user.application.port.in.UserQueryUseCase;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StudyUserServiceClientImpl implements UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(StudyUserServiceClientImpl.class);
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP_TYPE = new TypeReference<>() {};

    private final UserQueryUseCase userQueryUseCase;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> fetchUserInterestStocks(String userId) {
        try {
            UUID parsedUserId = UUID.fromString(userId);
            List<Map<String, Object>> favoriteStocks = objectMapper.convertValue(
                    userQueryUseCase.getFavoriteStocks(parsedUserId),
                    LIST_OF_MAP_TYPE
            );

            return favoriteStocks.stream()
                    .map(this::extractStockName)
                    .filter(name -> !name.isBlank())
                    .toList();
        } catch (Exception exception) {
            log.error("Failed to fetch user interest stocks for userId {}: {}", userId, exception.getMessage());
            return List.of();
        }
    }

    private String extractStockName(Map<String, Object> favoriteStock) {
        Object stockName = favoriteStock.get("name");
        return stockName == null ? "" : stockName.toString();
    }
}
