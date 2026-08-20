package depth.finvibe.modules.market.infra.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import depth.finvibe.common.error.DomainException;
import depth.finvibe.common.infra.error.ErrorResponse;
import depth.finvibe.common.infra.error.GlobalExceptionHandler;
import depth.finvibe.modules.market.domain.error.MarketErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MarketErrorHttpMapperTest {

    private final MarketErrorHttpMapper mapper = new MarketErrorHttpMapper();

    @Test
    @DisplayName("캔들 일시 장애 오류를 HTTP 503으로 매핑한다")
    void toStatus_candleTemporarilyUnavailable_serviceUnavailable() {
        assertThat(mapper.supports(MarketErrorCode.CANDLE_TEMPORARILY_UNAVAILABLE)).isTrue();
        assertThat(mapper.toStatus(MarketErrorCode.CANDLE_TEMPORARILY_UNAVAILABLE))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("캔들 일시 장애 DomainException은 500이 아닌 503 API 응답이 된다")
    void handleDomainException_candleTemporarilyUnavailable_returns503Response() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(List.of(mapper));

        ResponseEntity<ErrorResponse> response = handler.handleDomainException(
                new DomainException(MarketErrorCode.CANDLE_TEMPORARILY_UNAVAILABLE)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(503);
        assertThat(response.getBody().getCode()).isEqualTo("MARKET_CANDLE_TEMPORARILY_UNAVAILABLE");
    }
}
