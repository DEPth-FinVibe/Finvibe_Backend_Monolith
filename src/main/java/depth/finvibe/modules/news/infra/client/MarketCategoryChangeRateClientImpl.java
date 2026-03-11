package depth.finvibe.modules.news.infra.client;

import depth.finvibe.modules.market.application.port.in.CategoryQueryUseCase;
import depth.finvibe.modules.news.application.port.out.MarketCategoryChangeRatePort;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class MarketCategoryChangeRateClientImpl implements MarketCategoryChangeRatePort {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final CategoryQueryUseCase categoryQueryUseCase;
    private final ObjectMapper objectMapper;

    @Override
    public BigDecimal fetchAverageChangePct(Long categoryId) {
        try {
            Map<String, Object> response = objectMapper.convertValue(
                    categoryQueryUseCase.getCategoryChangeRate(categoryId),
                    MAP_TYPE
            );
            Object averageChangePct = response.get("averageChangePct");
            return averageChangePct == null ? null : new BigDecimal(averageChangePct.toString());
        } catch (Exception exception) {
            return null;
        }
    }
}
