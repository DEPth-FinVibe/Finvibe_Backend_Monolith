package depth.finvibe.modules.market.application.port.out;

import java.util.List;

import depth.finvibe.modules.market.dto.PriceCandleDto;

/**
 * 외부 시세 제공자의 캔들 조회 결과.
 *
 * @param candles 실제로 확보한 캔들
 * @param status 조회 완료 상태
 */
public record CandleFetchResult(
        List<PriceCandleDto.Response> candles,
        Status status
) {

    public CandleFetchResult {
        candles = candles == null ? List.of() : List.copyOf(candles);
    }

    public static CandleFetchResult complete(List<PriceCandleDto.Response> candles) {
        return new CandleFetchResult(candles, Status.COMPLETE);
    }

    public static CandleFetchResult partial(List<PriceCandleDto.Response> candles) {
        return new CandleFetchResult(candles, Status.PARTIAL);
    }

    public static CandleFetchResult failed() {
        return new CandleFetchResult(List.of(), Status.FAILED);
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public boolean isPartial() {
        return status == Status.PARTIAL;
    }

    public enum Status {
        COMPLETE,
        PARTIAL,
        FAILED
    }
}
