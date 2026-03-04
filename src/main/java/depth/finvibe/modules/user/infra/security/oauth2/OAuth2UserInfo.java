package depth.finvibe.modules.user.infra.security.oauth2;

import depth.finvibe.modules.user.domain.enums.AuthProvider;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class OAuth2UserInfo {
    private final String providerId;
    private final AuthProvider provider;
    private final String email;
    private final String name;

    public static OAuth2UserInfo of(String registrationId, Map<String, Object> attributes) {
        if ("google".equals(registrationId)) {
            return ofGoogle(attributes);
        } else if ("kakao".equals(registrationId)) {
            return ofKakao(attributes);
        } else if ("naver".equals(registrationId)) {
            return ofNaver(attributes);
        }
        throw new IllegalArgumentException("Unsupported provider: " + registrationId);
    }

    private static OAuth2UserInfo ofGoogle(Map<String, Object> attributes) {
        return OAuth2UserInfo.builder()
                .providerId((String) attributes.get("sub"))
                .provider(AuthProvider.GOOGLE)
                .email((String) attributes.get("email"))
                .name((String) attributes.get("name"))
                .build();
    }

    private static OAuth2UserInfo ofKakao(Map<String, Object> attributes) {
        if (attributes.containsKey("kakao_account")) {
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

            return OAuth2UserInfo.builder()
                    .providerId(String.valueOf(attributes.get("id")))
                    .provider(AuthProvider.KAKAO)
                    .email((String) kakaoAccount.get("email"))
                    .name(profile != null ? (String) profile.get("nickname") : null)
                    .build();
        }

        return OAuth2UserInfo.builder()
                .providerId(attributes.get("sub") != null ? attributes.get("sub").toString() : null)
                .provider(AuthProvider.KAKAO)
                .email((String) attributes.get("email"))
                .name((String) (attributes.get("nickname") != null ? attributes.get("nickname") : attributes.get("name")))
                .build();
    }

    private static OAuth2UserInfo ofNaver(Map<String, Object> attributes) {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");

        return OAuth2UserInfo.builder()
                .providerId((String) response.get("id"))
                .provider(AuthProvider.NAVER)
                .email((String) response.get("email"))
                .name((String) response.get("name"))
                .build();
    }
}
