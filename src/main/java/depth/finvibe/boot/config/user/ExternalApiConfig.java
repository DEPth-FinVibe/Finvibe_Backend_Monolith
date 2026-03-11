package depth.finvibe.boot.config.user;

import depth.finvibe.modules.user.infra.client.HttpMarketClient;
import depth.finvibe.modules.user.infra.client.HttpGamificationClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class ExternalApiConfig {

    @Value("${external.gamification.base-url:https://finvibe.com}")
    private String gamificationBaseUrl;

    private <T> T createClient(String baseUrl, Class<T> serviceType) {
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultStatusHandler(
                        HttpStatusCode::is4xxClientError,
                        (req, res) -> {

                        }
                )
                .build();

        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(serviceType);
    }

    @Bean
    HttpMarketClient userHttpMarketClient() {
        return createClient("http://market:80", HttpMarketClient.class);
    }

    @Bean
    HttpGamificationClient httpGamificationClient() {
        return createClient(gamificationBaseUrl, HttpGamificationClient.class);
    }
}
