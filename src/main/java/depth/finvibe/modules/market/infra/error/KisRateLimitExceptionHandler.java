package depth.finvibe.modules.market.infra.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import depth.finvibe.modules.market.domain.error.KisRateLimitExceededException;

/**
 * KIS API 레이트 리미트 초과 예외를 HTTP 응답으로 변환
 */
@RestControllerAdvice
public class KisRateLimitExceptionHandler {

  @ExceptionHandler(KisRateLimitExceededException.class)
  public ProblemDetail handleKisRateLimitExceeded(KisRateLimitExceededException ex) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.TOO_MANY_REQUESTS,
        ex.getMessage()
    );
    problemDetail.setTitle("KIS API Rate Limit Exceeded");
    problemDetail.setProperty("retryAfter", 1); // 1초 후 재시도 권장
    return problemDetail;
  }
}
