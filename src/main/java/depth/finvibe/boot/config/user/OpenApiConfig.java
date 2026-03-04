package depth.finvibe.boot.config.user;

import depth.finvibe.boot.security.AuthenticatedUser;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                        .title("Finvibe User API")
                        .description("Finvibe User 서비스 API 문서")
                        .version("v1"));
    }

    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("auth")
                .pathsToMatch("/auth/**")
                .pathsToExclude("/internal/**")
                .addOpenApiCustomizer(prefixPaths("/api/user"))
                .build();
    }

    @Bean
    public GroupedOpenApi memberApi() {
        return GroupedOpenApi.builder()
                .group("member")
                .pathsToMatch("/members/**")
                .pathsToExclude("/internal/**")
                .addOpenApiCustomizer(prefixPaths("/api/user"))
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
