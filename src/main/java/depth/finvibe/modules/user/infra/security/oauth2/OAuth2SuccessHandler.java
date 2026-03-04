package depth.finvibe.modules.user.infra.security.oauth2;

import depth.finvibe.modules.user.application.port.in.AuthCommandUseCase;
import depth.finvibe.modules.user.domain.enums.AuthProvider;
import depth.finvibe.modules.user.dto.UserDto;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthCommandUseCase authCommandUseCase;

    @Value("${app.oauth2.base-url}")
    private String baseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String registrationId = ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();

        OAuth2UserInfo userInfo = OAuth2UserInfo.of(registrationId, attributes);
        AuthProvider provider = userInfo.getProvider();
        String providerId = userInfo.getProviderId();
        String email = userInfo.getEmail();

        UserDto.OAuthLoginRequest loginRequest = UserDto.OAuthLoginRequest.builder()
                .provider(provider)
                .providerId(providerId)
                .build();

        UserDto.OAuthLoginResponse loginResponse = authCommandUseCase.oauthLogin(loginRequest);

        String targetUrl = determineTargetUrl(loginResponse, email);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String determineTargetUrl(UserDto.OAuthLoginResponse response, String email) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("registration_required", response.isRegistrationRequired());

        if (response.isRegistrationRequired()) {
            builder.queryParam("temporary_token", response.getTemporaryToken());
            if (email != null && !email.isBlank()) {
                builder.queryParam("email", email);
            }
        } else {
            builder.queryParam("access_token", response.getTokens().getAccessToken())
                    .queryParam("refresh_token", response.getTokens().getRefreshToken())
                    .queryParam("access_expires_at", response.getTokens().getAccessExpiresAt())
                    .queryParam("refresh_expires_at", response.getTokens().getRefreshExpiresAt());
        }

        return builder.build().toUriString();
    }
}
