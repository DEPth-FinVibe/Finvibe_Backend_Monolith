package depth.finvibe.modules.study.infra.client;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.study.application.port.out.UserServiceClient;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class StudyUserServiceClientImpl implements UserServiceClient {
    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://user")
            .build();
    private final ObjectMapper objectMapper;


    @Override
    public List<String> fetchUserInterestStocks(String userId) {
        try {
            String response = restClient.get()
                    .uri("/internal/members/{userId}/favorite-stocks", userId)
                    .retrieve()
                    .toString();

            List<FavoriteStockResponse> favoriteStocks = objectMapper.readValue(
                    response,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, FavoriteStockResponse.class)
            );

            return favoriteStocks.stream()
                    .map(FavoriteStockResponse::name)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to fetch user interest stocks for userId {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    private record FavoriteStockResponse(
            Long stockId,
            String name,
            String userId
    ){}
}
