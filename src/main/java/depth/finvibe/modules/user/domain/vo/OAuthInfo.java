package depth.finvibe.modules.user.domain.vo;

import depth.finvibe.modules.user.domain.enums.AuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OAuthInfo {
    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    private AuthProvider provider;

    @Column(name = "provider_id")
    private String providerId;

    public static OAuthInfo ofLocal() {
        return OAuthInfo.builder()
                .provider(AuthProvider.LOCAL)
                .build();
    }

    public static OAuthInfo ofSocial(AuthProvider provider, String providerId) {
        return OAuthInfo.builder()
                .provider(provider)
                .providerId(providerId)
                .build();
    }

    public boolean isSocial() {
        return provider != AuthProvider.LOCAL;
    }
}
