package depth.finvibe.modules.market.infra.config;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "market.kis")
@Validated
public record KisCredentialsProperties(
        @NotBlank
        String baseUrl,

        @Valid
        @NotNull
        List<Credential> credentials,

        @Valid
        CredentialLock credentialLock,

        @Valid
        Websocket websocket
) {
    /**
     * 빈 문자열로 설정된 credential을 필터링하여 유효한 것만 반환
     */
    public List<Credential> getValidCredentials() {
        return credentials.stream()
                .filter(c -> c.appKey() != null && !c.appKey().isBlank()
                        && c.appSecret() != null && !c.appSecret().isBlank())
                .collect(Collectors.toList());
    }

    public record Credential(
            String appKey,
            String appSecret
    ) {
    }

    public record Websocket(
            @NotBlank
            String url
    ) {
    }

    public record CredentialLock(
            Integer ttlSeconds,
            Integer renewIntervalSeconds,
            Integer retryMax,
            Long retryDelayMillis,
            String keyPrefix
    ) {
    }
}
