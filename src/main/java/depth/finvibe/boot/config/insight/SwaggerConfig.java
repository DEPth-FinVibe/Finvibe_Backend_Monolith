package depth.finvibe.boot.config.insight;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import depth.finvibe.boot.security.AuthenticatedUser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class SwaggerConfig {

        static {
                SpringDocUtils.getConfig()
                                .addAnnotationsToIgnore(AuthenticatedUser.class);
        }

        @Bean
        public OpenAPI finvibeOpenApi() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("Finvibe Insight API")
                                                .description("Finvibe Insight 서비스 API 문서")
                                                .version("v1"));
        }

        @Bean
        public GroupedOpenApi discussionApi() {
                return GroupedOpenApi.builder()
                                .group("discussion")
                                .pathsToMatch("/discussions/**")
                                .pathsToExclude("/internal/**")
                                .addOpenApiCustomizer(prefixPaths("/api/insight"))
                                .build();
        }

        @Bean
        public GroupedOpenApi newsApi() {
                return GroupedOpenApi.builder()
                                .group("news")
                                .pathsToMatch("/news/**", "/themes/**")
                                .pathsToExclude("/internal/**")
                                .addOpenApiCustomizer(prefixPaths("/api/insight"))
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
