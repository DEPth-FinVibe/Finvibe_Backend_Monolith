package depth.finvibe.modules.market.infra.websocket.kis.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.kafka.common.protocol.types.Field;

public class KisMessage {

    public enum TransactionType {
        Subscribe("1"),
        Unsubscribe("2");

        private final String code;

        TransactionType(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    @AllArgsConstructor(staticName = "of")
    @NoArgsConstructor
    @Data
    @Builder
    public static class TransactionRequest {
        public TransactionType type;
        public String stockKey;

        public RawTransactionRequest toRawRequest(String approvalKey) {
            return RawTransactionRequest.create(
                    approvalKey,
                    type.getCode(),
                    stockKey
            );
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class RawTransactionRequest {
        @JsonAlias("header")
        @JsonProperty("header")
        public RawTransactionRequestHeader header;

        @JsonAlias("body")
        @JsonProperty("body")
        public RawTransactionRequestBody body;

        public static RawTransactionRequest of(
                RawTransactionRequestHeader header,
                RawTransactionRequestBody body
        ) {
            RawTransactionRequest request = new RawTransactionRequest();
            request.header = header;
            request.body = body;
            return request;
        }

        public static RawTransactionRequest create(
                String approvalKey,
                String transactionType,
                String transactionKey
        ) {
            RawTransactionRequestHeader header = RawTransactionRequestHeader.withDefaults(approvalKey, transactionType);
            RawTransactionRequestBody body = RawTransactionRequestBody.withDefaults(transactionKey);
            return of(header, body);
        }
    }

    public static class RawTransactionRequestHeader {
        @JsonAlias("approval_key")
        @JsonProperty("approval_key")
        public String approvalKey;

        @JsonAlias("custtype")
        @JsonProperty("custtype")
        public String customerType;

        @JsonAlias("tr_type")
        @JsonProperty("tr_type")
        public String transactionType;

        @JsonAlias("content-type")
        @JsonProperty("content-type")
        public String contentType;

        public static RawTransactionRequestHeader of(
                String approvalKey,
                String customerType,
                String transactionType,
                String contentType
        ) {
            RawTransactionRequestHeader header = new RawTransactionRequestHeader();
            header.approvalKey = approvalKey;
            header.customerType = customerType;
            header.transactionType = transactionType;
            header.contentType = contentType;
            return header;
        }

        public static RawTransactionRequestHeader withDefaults(String approvalKey, String transactionType) {
            return of(approvalKey, "P", transactionType, "utf-8");
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class RawTransactionRequestBody {
        @JsonAlias("input")
        @JsonProperty("input")
        public RawTransactionRequestInput input;

        public static RawTransactionRequestBody of(String transactionId, String transactionKey) {
            return RawTransactionRequestBody.builder()
                    .input(RawTransactionRequestInput.of(transactionId, transactionKey))
                    .build();
        }

        public static RawTransactionRequestBody withDefaults(String tranctionKey) {
            return of("H0STCNT0", tranctionKey);
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class RawTransactionRequestInput {
        @JsonAlias("tr_id")
        @JsonProperty("tr_id")
        public String transactionId;

        @JsonAlias("tr_key")
        @JsonProperty("tr_key")
        public String transactionKey;

        public static RawTransactionRequestInput of(String transactionId, String transactionKey) {
            return RawTransactionRequestInput.builder()
                    .transactionId(transactionId)
                    .transactionKey(transactionKey)
                    .build();
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class TransactionResponse {
        @JsonAlias("MKSC_SHRN_ISCD")
        public String shortStockCode;

        @JsonAlias("STCK_CNTG_HOUR")
        public String stockExecutionTime;

        @JsonAlias("STCK_PRPR")
        public float currentStockPrice;

        @JsonAlias("PRDY_VRSS_SIGN")
        public String previousDayDiffSign;

        @JsonAlias("PRDY_VRSS")
        public float previousDayDiff;

        @JsonAlias("PRDY_CTRT")
        public float previousDayChangeRate;

        @JsonAlias("WGHN_AVRG_STCK_PRC")
        public float weightedAverageStockPrice;

        @JsonAlias("STCK_OPRC")
        public float openStockPrice;

        @JsonAlias("STCK_HGPR")
        public float highStockPrice;

        @JsonAlias("STCK_LWPR")
        public float lowStockPrice;

        @JsonAlias("ASKP1")
        public float sellPrice1;

        @JsonAlias("BIDP1")
        public float buyPrice1;

        @JsonAlias("CNTG_VOL")
        public float executionVolume;

        @JsonAlias("ACML_VOL")
        public float cumulativeVolume;

        @JsonAlias("ACML_TR_PBMN")
        public float cumulativeTradingAmount;

        @JsonAlias("SELN_CNTG_CSNU")
        public float sellExecutionCount;

        @JsonAlias("SHNU_CNTG_CSNU")
        public float buyExecutionCount;

        @JsonAlias("NTBY_CNTG_CSNU")
        public float netBuyExecutionCount;

        @JsonAlias("CTTR")
        public float executionStrength;

        @JsonAlias("SELN_CNTG_SMTN")
        public float totalSellQuantity;

        @JsonAlias("SHNU_CNTG_SMTN")
        public float totalBuyQuantity;

        @JsonAlias("CCLD_DVSN")
        public String executionType;

        @JsonAlias("SHNU_RATE")
        public float buyRate;

        @JsonAlias("PRDY_VOL_VRSS_ACML_VOL_RATE")
        public float previousDayVolumeChangeRate;

        @JsonAlias("OPRC_HOUR")
        public String openTime;

        @JsonAlias("OPRC_VRSS_PRPR_SIGN")
        public String openDiffSign;

        @JsonAlias("OPRC_VRSS_PRPR")
        public float openDiff;

        @JsonAlias("HGPR_HOUR")
        public String highTime;

        @JsonAlias("HGPR_VRSS_PRPR_SIGN")
        public String highDiffSign;

        @JsonAlias("HGPR_VRSS_PRPR")
        public float highDiff;

        @JsonAlias("LWPR_HOUR")
        public String lowTime;

        @JsonAlias("LWPR_VRSS_PRPR_SIGN")
        public String lowDiffSign;

        @JsonAlias("LWPR_VRSS_PRPR")
        public float lowDiff;

        @JsonAlias("BSOP_DATE")
        public String businessDate;

        @JsonAlias("NEW_MKOP_CLS_CODE")
        public String marketOperationCode;

        @JsonAlias("TRHT_YN")
        public String tradingHaltYn;

        @JsonAlias("ASKP_RSQN1")
        public float sellRemainingQuantity1;

        @JsonAlias("BIDP_RSQN1")
        public float buyRemainingQuantity1;

        @JsonAlias("TOTAL_ASKP_RSQN")
        public float totalSellRemainingQuantity;

        @JsonAlias("TOTAL_BIDP_RSQN")
        public float totalBuyRemainingQuantity;

        @JsonAlias("VOL_TNRT")
        public float turnoverRate;

        @JsonAlias("PRDY_SMNS_HOUR_ACML_VOL")
        public float previousDaySameTimeCumulativeVolume;

        @JsonAlias("PRDY_SMNS_HOUR_ACML_VOL_RATE")
        public float previousDaySameTimeCumulativeVolumeRate;

        @JsonAlias("HOUR_CLS_CODE")
        public String timeClassificationCode;

        @JsonAlias("MRKT_TRTM_CLS_CODE")
        public String marketCloseCode;

        @JsonAlias("VI_STND_PRC")
        public float viStandardPrice;
    }
}
