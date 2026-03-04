package depth.finvibe.modules.market.infra.websocket.kis.handler.parser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import depth.finvibe.modules.market.infra.websocket.kis.handler.KisEncryptionKeyStore;
import depth.finvibe.modules.market.infra.websocket.kis.handler.KisMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import depth.finvibe.modules.market.infra.websocket.kis.model.KisMessage;

@Slf4j
@RequiredArgsConstructor
public class KisDataMessageParser {
    private static final List<String> COLUMNS = List.of(
            "MKSC_SHRN_ISCD", "STCK_CNTG_HOUR", "STCK_PRPR", "PRDY_VRSS_SIGN",
            "PRDY_VRSS", "PRDY_CTRT", "WGHN_AVRG_STCK_PRC", "STCK_OPRC",
            "STCK_HGPR", "STCK_LWPR", "ASKP1", "BIDP1", "CNTG_VOL", "ACML_VOL",
            "ACML_TR_PBMN", "SELN_CNTG_CSNU", "SHNU_CNTG_CSNU", "NTBY_CNTG_CSNU",
            "CTTR", "SELN_CNTG_SMTN", "SHNU_CNTG_SMTN", "CCLD_DVSN", "SHNU_RATE",
            "PRDY_VOL_VRSS_ACML_VOL_RATE", "OPRC_HOUR", "OPRC_VRSS_PRPR_SIGN",
            "OPRC_VRSS_PRPR", "HGPR_HOUR", "HGPR_VRSS_PRPR_SIGN", "HGPR_VRSS_PRPR",
            "LWPR_HOUR", "LWPR_VRSS_PRPR_SIGN", "LWPR_VRSS_PRPR", "BSOP_DATE",
            "NEW_MKOP_CLS_CODE", "TRHT_YN", "ASKP_RSQN1", "BIDP_RSQN1",
            "TOTAL_ASKP_RSQN", "TOTAL_BIDP_RSQN", "VOL_TNRT",
            "PRDY_SMNS_HOUR_ACML_VOL", "PRDY_SMNS_HOUR_ACML_VOL_RATE",
            "HOUR_CLS_CODE", "MRKT_TRTM_CLS_CODE", "VI_STND_PRC"
    );

    private final KisEncryptionKeyStore encryptionKeyStore;
    private final KisMessageHandler messageHandler;

    public void handle(String message) {
        String[] parts = message.split("\\|", 4);
        if (parts.length < 4) {
            return;
        }

        String encryptedFlag = parts[0];
        String trId = parts[1];
        String payload = parts[3];

        String data = payload;
        if ("1".equals(encryptedFlag)) {
            KisEncryptionKeyStore.AesKeyIv keyIv = encryptionKeyStore.find(trId).orElse(null);
            if (keyIv == null) {
                log.warn("Missing encryption key for tr_id={}", trId);
                return;
            }
            data = decrypt(payload, keyIv);
            if (data == null) {
                return;
            }
        }

        String[] values = data.split("\\^");
        int columnSize = COLUMNS.size();
        for (int i = 0; i + columnSize <= values.length; i += columnSize) {
            Map<String, String> record = toRecord(values, i, columnSize);
            messageHandler.handleResponse(toResponse(record));
        }
    }

    private Map<String, String> toRecord(String[] values, int start, int columnSize) {
        Map<String, String> record = new HashMap<>();
        for (int i = 0; i < columnSize; i++) {
            record.put(COLUMNS.get(i), values[start + i]);
        }
        return record;
    }

    private KisMessage.TransactionResponse toResponse(Map<String, String> record) {
        return KisMessage.TransactionResponse.builder()
                .shortStockCode(text(record.get("MKSC_SHRN_ISCD")))
                .stockExecutionTime(text(record.get("STCK_CNTG_HOUR")))
                .currentStockPrice(number(record.get("STCK_PRPR")))
                .previousDayDiffSign(text(record.get("PRDY_VRSS_SIGN")))
                .previousDayDiff(number(record.get("PRDY_VRSS")))
                .previousDayChangeRate(number(record.get("PRDY_CTRT")))
                .weightedAverageStockPrice(number(record.get("WGHN_AVRG_STCK_PRC")))
                .openStockPrice(number(record.get("STCK_OPRC")))
                .highStockPrice(number(record.get("STCK_HGPR")))
                .lowStockPrice(number(record.get("STCK_LWPR")))
                .sellPrice1(number(record.get("ASKP1")))
                .buyPrice1(number(record.get("BIDP1")))
                .executionVolume(number(record.get("CNTG_VOL")))
                .cumulativeVolume(number(record.get("ACML_VOL")))
                .cumulativeTradingAmount(number(record.get("ACML_TR_PBMN")))
                .sellExecutionCount(number(record.get("SELN_CNTG_CSNU")))
                .buyExecutionCount(number(record.get("SHNU_CNTG_CSNU")))
                .netBuyExecutionCount(number(record.get("NTBY_CNTG_CSNU")))
                .executionStrength(number(record.get("CTTR")))
                .totalSellQuantity(number(record.get("SELN_CNTG_SMTN")))
                .totalBuyQuantity(number(record.get("SHNU_CNTG_SMTN")))
                .executionType(text(record.get("CCLD_DVSN")))
                .buyRate(number(record.get("SHNU_RATE")))
                .previousDayVolumeChangeRate(number(record.get("PRDY_VOL_VRSS_ACML_VOL_RATE")))
                .openTime(text(record.get("OPRC_HOUR")))
                .openDiffSign(text(record.get("OPRC_VRSS_PRPR_SIGN")))
                .openDiff(number(record.get("OPRC_VRSS_PRPR")))
                .highTime(text(record.get("HGPR_HOUR")))
                .highDiffSign(text(record.get("HGPR_VRSS_PRPR_SIGN")))
                .highDiff(number(record.get("HGPR_VRSS_PRPR")))
                .lowTime(text(record.get("LWPR_HOUR")))
                .lowDiffSign(text(record.get("LWPR_VRSS_PRPR_SIGN")))
                .lowDiff(number(record.get("LWPR_VRSS_PRPR")))
                .businessDate(text(record.get("BSOP_DATE")))
                .marketOperationCode(text(record.get("NEW_MKOP_CLS_CODE")))
                .tradingHaltYn(text(record.get("TRHT_YN")))
                .sellRemainingQuantity1(number(record.get("ASKP_RSQN1")))
                .buyRemainingQuantity1(number(record.get("BIDP_RSQN1")))
                .totalSellRemainingQuantity(number(record.get("TOTAL_ASKP_RSQN")))
                .totalBuyRemainingQuantity(number(record.get("TOTAL_BIDP_RSQN")))
                .turnoverRate(number(record.get("VOL_TNRT")))
                .previousDaySameTimeCumulativeVolume(number(record.get("PRDY_SMNS_HOUR_ACML_VOL")))
                .previousDaySameTimeCumulativeVolumeRate(number(record.get("PRDY_SMNS_HOUR_ACML_VOL_RATE")))
                .timeClassificationCode(text(record.get("HOUR_CLS_CODE")))
                .marketCloseCode(text(record.get("MRKT_TRTM_CLS_CODE")))
                .viStandardPrice(number(record.get("VI_STND_PRC")))
                .build();
    }

    private float number(String value) {
        if (value == null || value.isBlank()) {
            return 0.0f;
        }
        return Float.parseFloat(value.replace(",", ""));
    }

    private String text(String value) {
        if (value == null) {
            return "";
        }
        return value;
    }

    private String decrypt(String payload, KisEncryptionKeyStore.AesKeyIv keyIv) {
        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(payload);
            SecretKeySpec keySpec = new SecretKeySpec(keyIv.key().getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(keyIv.iv().getBytes(StandardCharsets.UTF_8));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return new String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("Failed to decrypt KIS payload.", ex);
            return null;
        }
    }
}
