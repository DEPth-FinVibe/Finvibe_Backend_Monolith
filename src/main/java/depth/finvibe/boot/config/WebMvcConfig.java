package depth.finvibe.boot.config;

import depth.finvibe.boot.security.JwtArgumentResolver;
import depth.finvibe.boot.security.LoginContextArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtArgumentResolver jwtArgumentResolver;
    private final LoginContextArgumentResolver loginContextArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(jwtArgumentResolver);
        resolvers.add(loginContextArgumentResolver);
    }
}
