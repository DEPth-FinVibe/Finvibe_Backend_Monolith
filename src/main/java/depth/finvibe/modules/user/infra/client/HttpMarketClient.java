package depth.finvibe.modules.user.infra.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;


//TODO: 아직 Market쪽의 엔드포인트가 확정되지 않음. 추후 수정 필요
@HttpExchange("/markets")
public interface HttpMarketClient {
    @GetExchange("/{id}")
    String getStockNameById(@PathVariable Long id);
}
