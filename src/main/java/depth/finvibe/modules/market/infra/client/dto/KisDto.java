package depth.finvibe.modules.market.infra.client.dto;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 한국투자증권 Open API 응답 및 요청 DTO 모음 클래스
 */
public class KisDto {

    /**
     * 종목조건검색조회 요청 DTO
     */
    public static class ConditionalStockSearchRequest {

    }

    /**
     * 종목조건검색조회 응답 DTO
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class ConditionalStockSearchResponse {
        private String rt_cd;
        private String msg_cd;
        private String msg1;
        private List<ConditionalStockSearchResponseItem> output2;
    }

    /**
     * 종목조건검색조회 응답 상세 항목 DTO
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class ConditionalStockSearchResponseItem {
        private String code;         // 종목코드
        private String name;         // 종목명
        private String daebi;        // 전일대비부호
        private String price;        // 현재가
        private String chgrate;      // 등락율
        private String acml_vol;     // 거래량
        private String trade_amt;    // 거래대금
        private String change;       // 전일대비
        private String cttr;         // 체결강도
        private String open;         // 시가
        private String high;         // 고가
        private String low;          // 저가
        private String high52;       // 52주최고가
        private String low52;        // 52주최저가
        private String expprice;     // 예상체결가
        private String expchange;    // 예상대비
        private String expchggrate;  // 예상등락률
        private String expcvol;      // 예상체결수량
        private String chgrate2;     // 전일거래량대비율
        private String expdaebi;     // 예상대비부호
        private String recprice;     // 기준가
        private String uplmtprice;   // 상한가
        private String dnlmtprice;   // 하한가
        private String stotprice;    // 시가총액
    }

    /**
     * 주식일별분봉조회 응답 DTO
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class TimeDailyChartPriceResponse {
        private String rt_cd;
        private String msg_cd;
        private String msg1;
        private TimeDailyChartPriceOutput1 output1;
        private List<TimeDailyChartPriceOutput2> output2;
    }

    /**
     * 주식일별분봉조회 응답 출력1 (당일 시세 요약 정보)
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class TimeDailyChartPriceOutput1 {
        private String prdy_vrss;
        private String prdy_vrss_sign;
        private String prdy_ctrt;
        private String stck_prdy_clpr;
        private String acml_vol;
        private String acml_tr_pbmn;
        private String hts_kor_isnm;
        private String stck_prpr;
        private String stck_bsop_date;
        private String stck_cntg_hour;
        private String stck_oprc;
        private String stck_hgpr;
        private String stck_lwpr;
        private String cntg_vol;
    }

    /**
     * 주식일별분봉조회 응답 출력2 (시간대별 상세 시세 데이터)
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class TimeDailyChartPriceOutput2 {
        private String stck_bsop_date;
        private String stck_cntg_hour;
        private String stck_prpr;
        private String stck_oprc;
        private String stck_hgpr;
        private String stck_lwpr;
        private String cntg_vol;
        private String acml_vol;
        private String acml_tr_pbmn;
    }

    /**
     * 국내업종 시간별지수(분) 응답 DTO
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class IndexTimePriceResponse {
        private String rt_cd;
        private String msg_cd;
        private String msg1;
        private IndexTimePriceOutput1 output1;
        private List<IndexTimePriceOutput> output2;
        private List<IndexTimePriceOutput> output;
    }

    /**
     * 국내업종 시간별지수(분) 응답 출력1 (당일 시세 요약 정보)
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class IndexTimePriceOutput1 {
        private String bstp_nmix_prdy_vrss;
        private String prdy_vrss_sign;
        private String bstp_nmix_prdy_ctrt;
        private String prdy_nmix;
        private String acml_vol;
        private String acml_tr_pbmn;
        private String hts_kor_isnm;
        private String bstp_nmix_prpr;
        private String bstp_cls_code;
        private String prdy_vol;
        private String bstp_nmix_oprc;
        private String bstp_nmix_hgpr;
        private String bstp_nmix_lwpr;
        private String futs_prdy_oprc;
        private String futs_prdy_hgpr;
        private String futs_prdy_lwpr;
    }

    /**
     * 국내업종 시간별지수(분) 응답 상세 항목 DTO
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class IndexTimePriceOutput {
        private String stck_bsop_date;
        private String stck_cntg_hour;
        private String bsop_hour;
        private String bstp_nmix_prpr;
        private String bstp_nmix_prdy_vrss;
        private String prdy_vrss_sign;
        private String bstp_nmix_prdy_ctrt;
        private String acml_tr_pbmn;
        private String acml_vol;
        private String cntg_vol;
        private String bstp_nmix_oprc;
        private String bstp_nmix_hgpr;
        private String bstp_nmix_lwpr;
    }

    /**
     * 국내주식기간별시세(일/주/월/년) 응답 DTO
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class DailyItemChartPriceResponse {
        private String rt_cd;
        private String msg_cd;
        private String msg1;
        private DailyItemChartPriceOutput1 output1;
        private List<DailyItemChartPriceOutput2> output2;
    }

    /**
     * 국내주식기간별시세 응답 출력1 (종목 기본 시세 및 지표 정보)
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class DailyItemChartPriceOutput1 {
        private String prdy_vrss;
        private String prdy_vrss_sign;
        private String prdy_ctrt;
        private String stck_prdy_clpr;
        private String acml_vol;
        private String acml_tr_pbmn;
        private String hts_kor_isnm;
        private String stck_prpr;
        private String stck_shrn_iscd;
        private String prdy_vol;
        private String stck_mxpr;
        private String stck_llam;
        private String stck_oprc;
        private String stck_hgpr;
        private String stck_lwpr;
        private String stck_prdy_oprc;
        private String stck_prdy_hgpr;
        private String stck_prdy_lwpr;
        private String askp;
        private String bidp;
        private String prdy_vrss_vol;
        private String vol_tnrt;
        private String stck_fcam;
        private String lstn_stcn;
        private String cpfn;
        private String hts_avls;
        private String per;
        private String per_nm;
        private String eps;
        private String pbr;
        private String itewhol_loan_rmnd_ratem;
    }

    /**
     * 국내주식기간별시세 응답 출력2 (기간별 상세 시세 데이터)
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class DailyItemChartPriceOutput2 {
        private String stck_bsop_date;
        private String stck_clpr;
        private String stck_oprc;
        private String stck_hgpr;
        private String stck_lwpr;
        private String acml_vol;
        private String acml_tr_pbmn;
        private String flng_cls_code;
        private String prtt_rate;
        private String mod_yn;
        private String prdy_vrss_sign;
        private String prdy_vrss;
        private String revl_issu_reas;
    }

    /**
     * 관심종목(멀티종목) 시세조회 응답 DTO
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class IntstockMultpriceResponse {
        private String rt_cd;
        private String msg_cd;
        private String msg1;
        private List<IntstockMultpriceResponseItem> output;
    }

    /**
     * 관심종목(멀티종목) 시세조회 응답 상세 항목 DTO
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class IntstockMultpriceResponseItem {
        private String kospi_kosdaq_cls_name; // 코스피 코스닥 구분 명
        private String mrkt_trtm_cls_name;    // 시장 조치 구분 명
        private String hour_cls_code;         // 시간 구분 코드
        private String inter_shrn_iscd;       // 관심 단축 종목코드
        private String inter_kor_isnm;        // 관심 한글 종목명
        private String inter2_prpr;           // 관심2 현재가
        private String inter2_prdy_vrss;      // 관심2 전일 대비
        private String prdy_vrss_sign;        // 전일 대비 부호
        private String prdy_ctrt;             // 전일 대비율
        private String acml_vol;              // 누적 거래량
        private String inter2_oprc;           // 관심2 시가
        private String inter2_hgpr;           // 관심2 고가
        private String inter2_lwpr;           // 관심2 저가
        private String inter2_llam;           // 관심2 하한가
        private String inter2_mxpr;           // 관심2 상한가
        private String inter2_askp;           // 관심2 매도호가
        private String inter2_bidp;           // 관심2 매수호가
        private String seln_rsqn;             // 매도 잔량
        private String shnu_rsqn;             // 매수2 잔량
        private String total_askp_rsqn;       // 총 매도호가 잔량
        private String total_bidp_rsqn;       // 총 매수호가 잔량
        private String acml_tr_pbmn;          // 누적 거래 대금
        private String inter2_prdy_clpr;      // 관심2 전일 종가
        private String oprc_vrss_hgpr_rate;   // 시가 대비 최고가 비율
        private String intr_antc_cntg_vrss;   // 관심 예상 체결 대비
        private String intr_antc_cntg_vrss_sign; // 관심 예상 체결 대비 부호
        private String intr_antc_cntg_prdy_ctrt; // 관심 예상 체결 전일 대비율
        private String intr_antc_vol;         // 관심 예상 거래량
        private String inter2_sdpr;           // 관심2 기준가
    }

    /**
     * API 요청 시 사용되는 종목 정보 DTO (시장 구분 코드 및 종목 코드)
     */
    @AllArgsConstructor(staticName = "of")
    @NoArgsConstructor
    @Data
    @Builder
    public static class StockInfo {
        private String marketCode;
        private String stockCode;
    }

    /**
     * 국내휴장일조회 응답 DTO
     * KIS API는 output을 단일 객체 또는 배열로 반환할 수 있어, 역직렬화 시 리스트로 통일합니다.
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class ChkHolidayResponse {
        private String rt_cd;
        private String msg_cd;
        private String msg1;
        @JsonDeserialize(using = ChkHolidayOutputListDeserializer.class)
        private List<ChkHolidayOutput> output;
    }

    /**
     * 국내휴장일조회 응답 상세 항목 DTO
     * 기준일자의 영업일/거래일/개장일/결제일 여부를 담습니다.
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class ChkHolidayOutput {
        private String bass_dt;       // 기준일자 (YYYYMMDD)
        private String wday_dvsn_cd;  // 요일구분코드
        private String bzdy_yn;       // 영업일여부
        private String tr_day_yn;     // 거래일여부
        private String opnd_yn;       // 개장일여부 (주문 가능 여부 판단 시 사용)
        private String sttl_day_yn;  // 결제일여부
    }
}
