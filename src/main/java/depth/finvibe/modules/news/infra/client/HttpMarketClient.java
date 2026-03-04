package depth.finvibe.modules.news.infra.client;

import depth.finvibe.modules.news.dto.MarketCategoryChangeRateResponse;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.bind.annotation.PathVariable;

@HttpExchange("/api/market/market")
public interface HttpMarketClient {

    @GetExchange("/categories/{categoryId}/change-rate")
    MarketCategoryChangeRateResponse getCategoryChangeRate(@PathVariable Long categoryId);
}
