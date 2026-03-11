package depth.finvibe.boot.config.investment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import depth.finvibe.modules.market.infra.client.KisCredentialAllocator;
import depth.finvibe.modules.market.infra.client.KisRateLimiter;
import depth.finvibe.modules.market.infra.client.tokenmanage.KisTokenManager;
import depth.finvibe.modules.market.infra.config.KisCredentialsProperties.Credential;

@Configuration
public class RestClientConfig {
  private final KisCredentialAllocator credentialAllocator;
  private final KisRateLimiter rateLimiter;
  private final KisTokenManager tokenManager;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public RestClientConfig(
      KisCredentialAllocator credentialAllocator,
      KisRateLimiter rateLimiter,
      KisTokenManager tokenManager
  ) {
    this.credentialAllocator = credentialAllocator;
    this.rateLimiter = rateLimiter;
    this.tokenManager = tokenManager;
  }

  @Bean
  @Qualifier("kisRestClient")
  public RestClient kisRestClient() {
    return RestClient.builder()
        .baseUrl("https://openapi.koreainvestment.com:9443")
        .defaultHeaders(headers -> {
          headers.add("Content-Type", "application/json");
          headers.add("custtype", "P");
        })
        .requestInterceptor((request, body, execution) -> {
          Credential credential = credentialAllocator.selectCredentialForRequest(rateLimiter);
          String accessToken = tokenManager.getAccessToken(credential);

          if (accessToken == null) {
            throw new IOException("Failed to obtain access token for KIS API");
          }

          request.getHeaders().set("appkey", credential.appKey());
          request.getHeaders().set("appsecret", credential.appSecret());
          request.getHeaders().add("Authorization", "Bearer " + accessToken);

          ClientHttpResponse response = execution.execute(request, body);

          // 응답을 캐싱하고 msg_cd 확인
          return new CachedBodyClientHttpResponse(response, credential.appKey(), rateLimiter, tokenManager, objectMapper);
        })
        .build();
  }

  /**
   * 응답 본문을 캐싱하여 여러 번 읽을 수 있도록 하는 래퍼 클래스
   * msg_cd를 확인하여 토큰 만료 및 레이트 리미트 에러를 감지하고, rt_cd를 확인하여 API 성공/실패를 검증합니다.
   */
  private static class CachedBodyClientHttpResponse implements ClientHttpResponse {
    private final ClientHttpResponse response;
    private final byte[] cachedBody;

    public CachedBodyClientHttpResponse(
        ClientHttpResponse response,
        String appKey,
        KisRateLimiter rateLimiter,
        KisTokenManager tokenManager,
        ObjectMapper objectMapper
    ) throws IOException {
      this.response = response;
      this.cachedBody = response.getBody().readAllBytes();

      // JSON 응답 검증
      try {
        JsonNode root = objectMapper.readTree(cachedBody);

        // 1. msg_cd 확인 (토큰 만료 및 레이트 리미트 에러)
        JsonNode msgCdNode = root.get("msg_cd");
        if (msgCdNode != null) {
          String msgCd = msgCdNode.asText();
          if ("EGW00123".equals(msgCd)) {
            // 토큰 만료 시 Redis에서 삭제
            tokenManager.invalidateToken(appKey);
          } else if ("EGW00201".equals(msgCd)) {
            // 레이트 리미트 처리
            rateLimiter.markAsExceeded(appKey);
          }
        }

        // 2. rt_cd 확인 (API 성공/실패)
        JsonNode rtCdNode = root.get("rt_cd");
        if (rtCdNode != null && !"0".equals(rtCdNode.asText())) {
          String rtCd = rtCdNode.asText();
          String msgCd = root.has("msg_cd") ? root.get("msg_cd").asText() : "Unknown";
          String msg1 = root.has("msg1") ? root.get("msg1").asText() : "Unknown error";
          throw new IOException("KIS API 호출 실패 - rt_cd: " + rtCd + ", msg_cd: " + msgCd + ", message: " + msg1);
        }
      } catch (IOException e) {
        throw e;
      } catch (Exception e) {
        // 파싱 실패 시 무시 (JSON이 아니거나 필드가 없는 경우)
      }
    }

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
      return response.getStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
      return response.getStatusText();
    }

    @Override
    public void close() {
      response.close();
    }

    @Override
    public InputStream getBody() throws IOException {
      return new ByteArrayInputStream(cachedBody);
    }

    @Override
    public HttpHeaders getHeaders() {
      return response.getHeaders();
    }
  }
}
