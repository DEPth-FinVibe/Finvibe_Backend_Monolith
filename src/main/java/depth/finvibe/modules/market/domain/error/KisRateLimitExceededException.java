package depth.finvibe.modules.market.domain.error;

/**
 * 모든 KIS API AppKey가 레이트 리미트에 도달했을 때 발생하는 예외
 * <p>
 * 클라이언트는 이 예외를 받으면 일정 시간(1초) 후 재시도해야 합니다.
 * </p>
 */
public class KisRateLimitExceededException extends RuntimeException {

  public KisRateLimitExceededException(String message) {
    super(message);
  }

  public KisRateLimitExceededException(String message, Throwable cause) {
    super(message, cause);
  }
}
