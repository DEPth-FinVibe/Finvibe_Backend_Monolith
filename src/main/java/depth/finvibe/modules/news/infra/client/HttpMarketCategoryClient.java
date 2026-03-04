package depth.finvibe.modules.news.infra.client;

import depth.finvibe.modules.news.dto.MarketCategoryResponse;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

@HttpExchange("/internal/market")
public interface HttpMarketCategoryClient {

    @GetExchange("/categories")
    List<MarketCategoryResponse> getCategories();
}
