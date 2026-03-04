package depth.finvibe.boot.config.gamification;

import depth.finvibe.boot.security.AuthenticatedUser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.utils.SpringDocUtils;
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
                        .title("Finvibe Gamification API")
                        .description("Finvibe Gamification 서비스 API 문서")
                        .version("v1"));
    }

    @Bean
    public GroupedOpenApi marketApi() {
        return GroupedOpenApi.builder()
                .group("gamification")
                .pathsToMatch("/badges/**", "/challenges/**", "/squads/**", "/xp/**")
                .pathsToExclude("/internal/**")
                .addOpenApiCustomizer(prefixPaths("/api/gamification"))
                .build();
    }

    @Bean
    public GroupedOpenApi studyApi() {
        return GroupedOpenApi.builder()
                .group("study")
                .pathsToMatch("/study/**")
                .pathsToExclude("/internal/**")
                .addOpenApiCustomizer(prefixPaths("/api/study"))
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
