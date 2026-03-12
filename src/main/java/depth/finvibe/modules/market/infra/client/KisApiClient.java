package depth.finvibe.modules.market.infra.client;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import depth.finvibe.modules.market.application.port.out.IndexPriceClient;
import depth.finvibe.modules.market.application.port.out.IndexTimePriceSnapshot;
import depth.finvibe.modules.market.domain.enums.MarketIndexType;
import depth.finvibe.modules.market.infra.client.dto.KisDto;
import depth.finvibe.common.error.DomainException;
import depth.finvibe.common.error.GlobalErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 한국투자증권 Open API 클라이언트
 */
@Slf4j
@Component
public class KisApiClient implements IndexPriceClient {

        private final RestClient restClient;
        private final RestClient restClientBatch;
        private final String kisUserId;

        public KisApiClient(
                @Qualifier("kisRestClient") RestClient restClient,
                @Qualifier("kisRestClientBatch") RestClient restClientBatch,
                @Value("${market.kis.user-id}") String kisUserId) {
                this.restClient = restClient;
                this.restClientBatch = restClientBatch;
                this.kisUserId = kisUserId;
        }

        /**
         * <a href=
         * "https://apiportal.koreainvestment.com/apiservice-apiservice?/uapi/domestic-stock/v1/quotations/psearch-result">종목조건검색조회
         * API</a>
         * 거래대금, 거래량, 상승률, 하락률 등 특정 조건으로 상위 종목들을 검색합니다.
         * 
         * @param condition 조건 번호
         * @return 조건에 해당하는 종목 리스트
         */
        @CircuitBreaker(name = "kisStockSearch", fallbackMethod = "fallbackConditionalStockSearch")
        public List<KisDto.ConditionalStockSearchResponseItem> fetchConditionalStockSearch(ConditionSeq condition) {
                return Objects.requireNonNull(
                                restClient.get()
                                                .uri("/uapi/domestic-stock/v1/quotations/psearch-result" +
                                                                "?user_id=" + kisUserId +
                                                                "&seq=" + condition.getSeq())
                                                .headers(h -> h.set("tr_id", "HHKST03900400"))
                                                .retrieve()
                                                .body(KisDto.ConditionalStockSearchResponse.class))
                                .getOutput2();
        }

        /**
         * <a href=
         * "https://apiportal.koreainvestment.com/apiservice-apiservice?/uapi/domestic-stock/v1/quotations/inquire-time-dailychartprice">주식일별분봉조회
         * API</a>
         * 특정 시간 기준으로 과거 2시간 동안의 1분봉 차트 데이터를 조회합니다.
         * 최대 120개의 분봉만 한번에 조회할 수 있음.
         * 조회할 시간부터 2시간 전의 시간까지 조회됨 (예: 130000 조회 시 130000~110000 1분 단위로 120개 조회됨, 순서는
         * 최신 데이터가 먼저)
         * 
         * @param marketCode      시장 구분 코드
         * @param stockCode       종목 코드
         * @param time            조회할 시간 (HHMMSS)
         * @param date            조회할 일자 (YYYYMMDD)
         * @param includePastData 과거 데이터 포함 여부
         * @param includeFakeTick 모의 틱 포함 여부
         */
        @CircuitBreaker(name = "kisChartData", fallbackMethod = "fallbackTimeDailyChartPrice")
        public KisDto.TimeDailyChartPriceResponse fetchTimeDailyChartPrice(
                        String marketCode,
                        String stockCode,
                        String time,
                        String date,
                        String includePastData,
                        String includeFakeTick) {
                String pastDataIncu = includePastData == null ? "N" : includePastData;
                String fakeTickIncu = includeFakeTick == null ? "" : includeFakeTick;

                return Objects.requireNonNull(
                                restClient.get()
                                                .uri("/uapi/domestic-stock/v1/quotations/inquire-time-dailychartprice" +
                                                                "?FID_COND_MRKT_DIV_CODE=" + marketCode +
                                                                "&FID_INPUT_ISCD=" + stockCode +
                                                                "&FID_INPUT_HOUR_1=" + time +
                                                                "&FID_INPUT_DATE_1=" + date +
                                                                "&FID_PW_DATA_INCU_YN=" + pastDataIncu +
                                                                "&FID_FAKE_TICK_INCU_YN=" + fakeTickIncu)
                                                .headers(h -> h.set("tr_id", "FHKST03010230"))
                                                .retrieve()
                                                .body(KisDto.TimeDailyChartPriceResponse.class));
        }

        /**
         * <a href=
         * "https://apiportal.koreainvestment.com/apiservice-apiservice?/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice">국내주식기간별시세(일/주/월/년)
         * API</a>
         * 일/주/월/년 단위로 특정 기간 동안의 주가 차트 데이터를 조회합니다.
         */
        @CircuitBreaker(name = "kisChartData", fallbackMethod = "fallbackDailyItemChartPrice")
        public KisDto.DailyItemChartPriceResponse fetchDailyItemChartPrice(
                        String marketCode,
                        String stockCode,
                        String startDate,
                        String endDate,
                        String periodCode,
                        String originalAdjustedPriceFlag) {
                return Objects.requireNonNull(
                                restClient.get()
                                                .uri("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice" +
                                                                "?FID_COND_MRKT_DIV_CODE=" + marketCode +
                                                                "&FID_INPUT_ISCD=" + stockCode +
                                                                "&FID_INPUT_DATE_1=" + startDate +
                                                                "&FID_INPUT_DATE_2=" + endDate +
                                                                "&FID_PERIOD_DIV_CODE=" + periodCode +
                                                                "&FID_ORG_ADJ_PRC=" + originalAdjustedPriceFlag)
                                                .headers(h -> h.set("tr_id", "FHKST03010100"))
                                                .retrieve()
                                                .body(KisDto.DailyItemChartPriceResponse.class));
        }

        /**
         * <a href=
         * "https://apiportal.koreainvestment.com/apiservice-apiservice?/uapi/domestic-stock/v1/quotations/inquire-time-indexchartprice">업종 분봉조회
         * API</a>
         * 코스피/코스닥 등 국내 업종 지수를 분 단위로 조회합니다.
         */
        @CircuitBreaker(name = "kisChartData", fallbackMethod = "fallbackIndexTimePrice")
        public List<KisDto.IndexTimePriceOutput> fetchIndexTimePrice(
                        IndexCode indexCode,
                        String intervalSec) {
                String resolvedIntervalSec = intervalSec == null || intervalSec.isBlank() ? "60" : intervalSec;

                KisDto.IndexTimePriceResponse response = Objects.requireNonNull(
                        restClient.get()
                                .uri("/uapi/domestic-stock/v1/quotations/inquire-index-timeprice" +
                                        "?FID_INPUT_HOUR_1=" + resolvedIntervalSec +
                                        "&FID_INPUT_ISCD=" + indexCode.getCode() +
                                        "&FID_ETC_CLS_CODE=" + "1" +
                                        "&FID_PW_DATA_INCU_YN=" + "Y" +
                                        "&FID_COND_MRKT_DIV_CODE=U")
                                .headers(h -> h.set("tr_id", "FHKUP03500200"))
                                .retrieve()
                                .body(KisDto.IndexTimePriceResponse.class));


                if (response.getOutput2() != null && !response.getOutput2().isEmpty()) {
                        return response.getOutput2();
                }
                if (response.getOutput() != null && !response.getOutput().isEmpty()) {
                        return response.getOutput();
                }
                return List.of();
        }

        @Override
        public List<IndexTimePriceSnapshot> fetchIndexTimePrices(MarketIndexType indexType) {
                return fetchIndexTimePrice(toIndexCode(indexType), "60").stream()
                                .map(output -> new IndexTimePriceSnapshot(
                                                output.getStck_bsop_date(),
                                                output.getStck_cntg_hour(),
                                                output.getBsop_hour(),
                                                output.getBstp_nmix_oprc(),
                                                output.getBstp_nmix_hgpr(),
                                                output.getBstp_nmix_lwpr(),
                                                output.getBstp_nmix_prpr(),
                                                output.getBstp_nmix_prdy_ctrt(),
                                                output.getCntg_vol(),
                                                output.getAcml_tr_pbmn()))
                                .toList();
        }

        /**
         * <a href=
         * "https://apiportal.koreainvestment.com/apiservice-apiservice?/uapi/domestic-stock/v1/quotations/intstock-multprice">관심종목(멀티종목)
         * 시세조회 API [국내주식-205]</a>
         * 한 번의 API 호출로 최대 30개 종목의 실시간 시세 정보를 동시에 조회합니다.
         * 
         * @param stocks 종목 정보 리스트 (최대 30개)
         * @return 관심종목 시세 리스트
         */
        @CircuitBreaker(name = "kisRealtimePrice", fallbackMethod = "fallbackIntstockMultprice")
        public List<KisDto.IntstockMultpriceResponseItem> fetchIntstockMultprice(List<KisDto.StockInfo> stocks) {
                if (stocks == null || stocks.isEmpty()) {
                        return List.of();
                }
                if (stocks.size() > 30) {
                        throw new IllegalArgumentException("최대 30종목까지 조회 가능합니다.");
                }

                String uri = buildMultiStockUri(stocks);
                return Objects.requireNonNull(
                                restClient.get()
                                                .uri(uri)
                                                .headers(h -> h.set("tr_id", "FHKST11300006"))
                                                .retrieve()
                                                .body(KisDto.IntstockMultpriceResponse.class))
                                .getOutput();
        }

        /**
         * <a href=
         * "https://apiportal.koreainvestment.com/apiservice-apiservice?/uapi/domestic-stock/v1/quotations/chk-holiday">국내휴장일조회
         * API</a>
         * 기준일자의 영업일/거래일/개장일/결제일 여부를 조회합니다.
         * 주문 가능 여부는 응답의 개장일여부(opnd_yn)로 판단하면 됩니다.
         * 단시간 내 다수 호출 시 서비스에 영향을 줄 수 있으므로 가급적 1일 1회 호출을 권장합니다.
         *
         * @param bassDt 기준일자 (YYYYMMDD)
         * @return 해당 일자의 휴장일 정보 리스트 (보통 1건)
         */
        @CircuitBreaker(name = "kisChkHoliday", fallbackMethod = "fallbackChkHoliday")
        public List<KisDto.ChkHolidayOutput> fetchChkHoliday(String bassDt) {
                KisDto.ChkHolidayResponse body = Objects.requireNonNull(
                                restClient.get()
                                                .uri(uriBuilder -> uriBuilder
                                                                .path("/uapi/domestic-stock/v1/quotations/chk-holiday")
                                                                .queryParam("BASS_DT", bassDt)
                                                                .queryParam("CTX_AREA_FK", "")
                                                                .queryParam("CTX_AREA_NK", "")
                                                                .build())
                                                .headers(h -> h.set("tr_id", "CTCA0903R"))
                                                .retrieve()
                                                .body(KisDto.ChkHolidayResponse.class));
                List<KisDto.ChkHolidayOutput> output = body.getOutput();
                return output != null ? output : List.of();
        }

        private String buildMultiStockUri(List<KisDto.StockInfo> stocks) {
                StringBuilder uri = new StringBuilder("/uapi/domestic-stock/v1/quotations/intstock-multprice?");
                for (int i = 0; i < stocks.size(); i++) {
                        if (i > 0) {
                                uri.append("&");
                        }
                        KisDto.StockInfo stock = stocks.get(i);
                        int index = i + 1;
                        uri.append("FID_COND_MRKT_DIV_CODE_").append(index).append("=").append(stock.getMarketCode())
                                        .append("&")
                                        .append("FID_INPUT_ISCD_").append(index).append("=")
                                        .append(stock.getStockCode());
                }
                return uri.toString();
        }

        private IndexCode toIndexCode(MarketIndexType indexType) {
                return switch (indexType) {
                        case KOSPI -> IndexCode.KOSPI;
                        case KOSDAQ -> IndexCode.KOSDAQ;
                };
        }

        @RequiredArgsConstructor
        @Getter
        public enum ConditionSeq {
                TRADE_VALUE(0),
                VOLUME(1),
                RISE_RATE(2),
                FALL_RATE(3);

                private final int seq;
        }

        @RequiredArgsConstructor
        @Getter
        public enum IndexCode {
                KOSPI("0001"),
                KOSDAQ("1001");

                private final String code;
        }

        // ===== Fallback Method =====

        /**
         * 모든 KIS API 호출 실패 시 공통 fallback 메서드
         * Circuit Breaker가 열리면 이 메서드가 호출되어 503 에러를 반환합니다.
         */
        @SuppressWarnings("unused")
        private List<KisDto.ConditionalStockSearchResponseItem> fallbackConditionalStockSearch(
                        ConditionSeq condition,
                        Throwable throwable) {
                log.error("KIS API Circuit Breaker 작동 - fetchConditionalStockSearch, condition={}", condition,
                                throwable);
                throw new DomainException(GlobalErrorCode.CIRCUIT_BREAKER_OPEN);
        }

        @SuppressWarnings("unused")
        private KisDto.TimeDailyChartPriceResponse fallbackTimeDailyChartPrice(
                        String marketCode,
                        String stockCode,
                        String time,
                        String date,
                        String includePastData,
                        String includeFakeTick,
                        Throwable throwable) {
                log.error(
                                "KIS API Circuit Breaker 작동 - fetchTimeDailyChartPrice, marketCode={}, stockCode={}, time={}, date={}",
                                marketCode,
                                stockCode,
                                time,
                                date,
                                throwable);
                throw new DomainException(GlobalErrorCode.CIRCUIT_BREAKER_OPEN);
        }

        @SuppressWarnings("unused")
        private KisDto.DailyItemChartPriceResponse fallbackDailyItemChartPrice(
                        String marketCode,
                        String stockCode,
                        String startDate,
                        String endDate,
                        String periodCode,
                        String originalAdjustedPriceFlag,
                        Throwable throwable) {
                log.error(
                                "KIS API Circuit Breaker 작동 - fetchDailyItemChartPrice, marketCode={}, stockCode={}, startDate={}, endDate={}, periodCode={}",
                                marketCode,
                                stockCode,
                                startDate,
                                endDate,
                                periodCode,
                                throwable);
                throw new DomainException(GlobalErrorCode.CIRCUIT_BREAKER_OPEN);
        }

        @SuppressWarnings("unused")
        private List<KisDto.IndexTimePriceOutput> fallbackIndexTimePrice(
                        IndexCode indexCode,
                        String intervalSec,
                        Throwable throwable) {
                log.error(
                                "KIS API Circuit Breaker 작동 - fetchIndexTimePrice, indexCode={}, intervalSec={}",
                                indexCode,
                                intervalSec,
                                throwable);
                throw new DomainException(GlobalErrorCode.CIRCUIT_BREAKER_OPEN);
        }

        @CircuitBreaker(name = "kisBatchPrice", fallbackMethod = "fallbackIntstockMultpriceBatch")
        public List<KisDto.IntstockMultpriceResponseItem> fetchIntstockMultpriceBatch(List<KisDto.StockInfo> stocks) {
                if (stocks == null || stocks.isEmpty()) {
                        return List.of();
                }
                if (stocks.size() > 30) {
                        throw new IllegalArgumentException("최대 30종목까지 조회 가능합니다.");
                }

                String uri = buildMultiStockUri(stocks);
                return Objects.requireNonNull(
                                restClientBatch.get()
                                                .uri(uri)
                                                .headers(h -> h.set("tr_id", "FHKST11300006"))
                                                .retrieve()
                                                .body(KisDto.IntstockMultpriceResponse.class))
                                .getOutput();
        }

        @SuppressWarnings("unused")
        private List<KisDto.IntstockMultpriceResponseItem> fallbackIntstockMultprice(
                        List<KisDto.StockInfo> stocks,
                        Throwable throwable) {
                log.error(
                                "KIS API Circuit Breaker 작동 - fetchIntstockMultprice, stocksCount={}",
                                stocks == null ? 0 : stocks.size(),
                                throwable);
                log.error("KIS API Circuit Breaker 작동: {}", throwable.getMessage(), throwable);
                throw new DomainException(GlobalErrorCode.CIRCUIT_BREAKER_OPEN);
        }

        @SuppressWarnings("unused")
        private List<KisDto.IntstockMultpriceResponseItem> fallbackIntstockMultpriceBatch(
                        List<KisDto.StockInfo> stocks,
                        Throwable throwable) {
                log.error(
                                "KIS API Circuit Breaker 작동 - fetchIntstockMultpriceBatch, stocksCount={}",
                                stocks == null ? 0 : stocks.size(),
                                throwable);
                throw new DomainException(GlobalErrorCode.CIRCUIT_BREAKER_OPEN);
        }

        @SuppressWarnings("unused")
        private List<KisDto.ChkHolidayOutput> fallbackChkHoliday(String bassDt, Throwable throwable) {
                log.error("KIS API Circuit Breaker 작동 - fetchChkHoliday, bassDt={}", bassDt, throwable);
                throw new DomainException(GlobalErrorCode.CIRCUIT_BREAKER_OPEN);
        }
}
