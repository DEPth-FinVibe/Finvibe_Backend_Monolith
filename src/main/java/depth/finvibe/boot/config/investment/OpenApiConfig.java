package depth.finvibe.boot.config.investment;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Paths;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import depth.finvibe.boot.security.AuthenticatedUser;

@Configuration
public class OpenApiConfig {

    static {
        SpringDocUtils.getConfig()
                .addAnnotationsToIgnore(AuthenticatedUser.class);
    }

    @Bean
    public OpenAPI finvibeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Finvibe Investment API")
                        .description("Finvibe Investment 서비스 API 문서")
                        .version("v1"));
    }

    @Bean
    public GroupedOpenApi assetApi() {
        return GroupedOpenApi.builder()
                .group("asset")
                .pathsToMatch("/portfolios/**", "/assets/**", "/rankings/**")
                .pathsToExclude("/internal/**")
                .addOpenApiCustomizer(prefixPaths("/api/asset"))
                .build();
    }

    @Bean
    public GroupedOpenApi walletApi() {
        return GroupedOpenApi.builder()
                .group("wallet")
                .pathsToMatch("/wallets/**")
                .pathsToExclude("/internal/**")
                .addOpenApiCustomizer(prefixPaths("/api/wallet"))
                .build();
    }

    @Bean
    public GroupedOpenApi tradeApi() {
        return GroupedOpenApi.builder()
                .group("trade")
                .pathsToMatch("/trades/**")
                .pathsToExclude("/internal/**")
                .addOpenApiCustomizer(prefixPaths("/api/trade"))
                .build();
    }

    @Bean
    public GroupedOpenApi marketApi() {
        return GroupedOpenApi.builder()
                .group("market")
                .pathsToMatch("/market/**")
                .pathsToExclude("/internal/**")
                .addOpenApiCustomizer(prefixPaths("/api/market"))
                .build();
    }

    private OpenApiCustomizer prefixPaths(String prefix) {
        return openApi -> {
            if (openApi.getPaths() == null || openApi.getPaths().isEmpty()) {
                return;
            }

            Paths prefixedPaths = new Paths();
            openApi.getPaths().forEach((path, item) -> prefixedPaths.addPathItem(prefix + path, item));
            openApi.setPaths(prefixedPaths);
        };
    }
}
