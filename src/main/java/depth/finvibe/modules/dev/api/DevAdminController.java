package depth.finvibe.modules.dev.api;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.user.domain.enums.UserRole;
import depth.finvibe.modules.market.application.BatchPriceUpdateService;
import depth.finvibe.modules.market.infra.client.tokenmanage.repository.TokenRepository;
import depth.finvibe.common.error.DomainException;
import depth.finvibe.common.error.GlobalErrorCode;

@Hidden
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
public class DevAdminController {

  private final TokenRepository tokenRepository;
  private final BatchPriceUpdateService batchPriceUpdateService;

  @GetMapping("/kis/token")
  @Operation(summary = "KIS 토큰 조회", description = "appKey로 Redis에 저장된 KIS access token을 조회합니다.")
  public ResponseEntity<KisTokenResponse> getKisToken(
      @Parameter(description = "KIS appKey", example = "appKey1234") @RequestParam String appKey,
      @Parameter(hidden = true) @AuthenticatedUser Requester requester
  ) {
    ensureAdmin(requester);

    String token = tokenRepository.getAccessToken(appKey);
    LocalDateTime expiresAt = tokenRepository.getExpiresAt(appKey);

    return ResponseEntity.ok(new KisTokenResponse(appKey, token, expiresAt, token != null));
  }

  @PostMapping("/market/batch-price-update")
  @Operation(summary = "배치 가격 업데이트 강제 실행", description = "시장 상태와 무관하게 배치 가격 업데이트를 즉시 실행합니다.")
  public ResponseEntity<Void> runBatchPriceUpdate(
      @Parameter(hidden = true) @AuthenticatedUser Requester requester
  ) {
    ensureAdmin(requester);
    batchPriceUpdateService.updateHoldingStockPrices();
    return ResponseEntity.ok().build();
  }

  private void ensureAdmin(Requester requester) {
    if (requester.getRole() != UserRole.ADMIN) {
      throw new DomainException(GlobalErrorCode.ACCESS_DENIED);
    }
  }

  public record KisTokenResponse(
      String appKey,
      String token,
      LocalDateTime expiresAt,
      boolean exists
  ) {
  }
}
