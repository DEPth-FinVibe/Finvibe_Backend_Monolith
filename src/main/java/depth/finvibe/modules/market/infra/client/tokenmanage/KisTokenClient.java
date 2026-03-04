package depth.finvibe.modules.market.infra.client.tokenmanage;

import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KisTokenClient {
    private final RestClient tokenClient;

    public KisTokenClient() {
        this.tokenClient = RestClient.builder()
                .baseUrl("https://openapi.koreainvestment.com:9443")
                .build();
    }

    public TokenResponse requestAccessToken(String apiKey, String apiSecret) {
        KisTokenResponse response = tokenClient.post()
                .uri("/oauth2/tokenP")
                .body(
                        KisTokenRequest.builder()
                                .grant_type("client_credentials")
                                .appkey(apiKey)
                                .appsecret(apiSecret)
                                .build()
                )
                .retrieve()
                .body(KisTokenResponse.class);

        KisTokenResponse safeResponse = Objects.requireNonNull(response);
        return new TokenResponse(
                safeResponse.getAccess_token(),
                safeResponse.getExpires_in().longValue()
        );
    }

    public record TokenResponse(String accessToken, long expiresIn) {
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class KisTokenRequest {
        private String grant_type;
        private String appkey;
        private String appsecret;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class KisTokenResponse {
        private String access_token;
        private String token_type;
        private Float expires_in;
        private String access_token_token_expired;
    }
}
