package depth.finvibe.modules.market.infra.client;

import depth.finvibe.modules.market.dto.StockDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
public class ElwKisFileClient implements KisFileClient {

    private static final String ELW_ZIP_URL = "https://new.real.download.dws.co.kr/common/master/elw_code.mst.zip";
    private static final String ELW_MST_NAME = "elw_code.mst";
    private static final Charset KIS_CHARSET = Charset.forName("MS949"); // cp949

    @Override
    public List<StockDto.RealMarketStockResponse> fetchStocksInKisFile() {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("kis-elw-");
            Path zipPath = tempDir.resolve("elw_code.zip");
            downloadFile(ELW_ZIP_URL, zipPath);
            Path mstPath = unzipToFile(zipPath, tempDir, ELW_MST_NAME);
            return parseElwFile(mstPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load ELW master file", e);
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    private void downloadFile(String url, Path target) throws IOException {
        try (InputStream inputStream = new URL(url).openStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path unzipToFile(Path zipPath, Path targetDir, String targetName) throws IOException {
        Path outPath = targetDir.resolve(targetName);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (targetName.equals(entry.getName())) {
                    Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                    return outPath;
                }
            }
        }
        throw new IOException("Missing " + targetName + " in zip");
    }

    private List<StockDto.RealMarketStockResponse> parseElwFile(Path mstPath) throws IOException {
        List<StockDto.RealMarketStockResponse> result = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(mstPath, KIS_CHARSET)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }

                ElwMasterRow row = parseMasterRow(line);
                
                result.add(StockDto.RealMarketStockResponse.builder()
                        .symbol(row.mkscShrnIscd)
                        .name(row.htsKorIsnm)
                        .typeCode(row.elwNvltOptnClsCode)
                        .build());
            }
        }
        return result;
    }

    private ElwMasterRow parseMasterRow(String row) {
        // 파이썬 파싱 로직에 따른 필드 위치
        // mksc_shrn_iscd = row[0:9]
        String mkscShrnIscd = safeSubstring(row, 0, 9).trim();
        
        // stnd_iscd = row[9:21]
        // String stndIscd = safeSubstring(row, 9, 21).trim();
        
        // hts_kor_isnm = row[21:50]
        String htsKorIsnm = safeSubstring(row, 21, 50).trim();
        
        // crow = row[50:]
        String crow = safeSubstring(row, 50, row.length());
        
        // elw_nvlt_optn_cls_code = crow[:1] (ELW 권리형태)
        String elwNvltOptnClsCode = safeSubstring(crow, 0, 1).trim();
        
        // elw_ko_barrier = crow[1:14] (ELW 조기종료발생기준가격)
        String elwKoBarrier = safeSubstring(crow, 1, 14).trim();
        
        // bskt_yn = crow[14:15] (바스켓 여부)
        String bsktYn = safeSubstring(crow, 14, 15).trim();
        
        // unas_iscd1 = crow[15:24] (기초자산코드1)
        String unasIscd1 = safeSubstring(crow, 15, 24).trim();
        
        // elw_pblc_istu_name = row[-11:-110] (발행사 한글 종목명)
        String elwPblcIstuName = safeSubstring(row, row.length() - 121, row.length() - 110).trim();
        
        // elw_pblc_mrkt_prtt_no = row[-110:-105] (발행사코드)
        String elwPblcMrktPrttNo = safeSubstring(row, row.length() - 110, row.length() - 105).trim();
        
        // acpr = row[-105:-96] (행사가)
        String acpr = safeSubstring(row, row.length() - 105, row.length() - 96).trim();
        
        // stck_last_tr_month = row[-96:-88] (최종거래일)
        String stckLastTrMonth = safeSubstring(row, row.length() - 96, row.length() - 88).trim();
        
        return new ElwMasterRow(
            mkscShrnIscd, 
            htsKorIsnm, 
            elwNvltOptnClsCode,
            elwKoBarrier,
            bsktYn,
            unasIscd1,
            elwPblcIstuName,
            elwPblcMrktPrttNo,
            acpr,
            stckLastTrMonth
        );
    }

    private String safeSubstring(String str, int start, int end) {
        if (str == null) {
            return "";
        }
        int length = str.length();
        int safeStart = Math.max(0, Math.min(start, length));
        int safeEnd = Math.max(safeStart, Math.min(end, length));
        return str.substring(safeStart, safeEnd);
    }

    private static class ElwMasterRow {
        private final String mkscShrnIscd;           // 단축코드
        private final String htsKorIsnm;             // 한글 종목명
        private final String elwNvltOptnClsCode;     // ELW권리형태
        private final String elwKoBarrier;           // ELW조기종료발생기준가격
        private final String bsktYn;                 // 바스켓 여부
        private final String unasIscd1;              // 기초자산코드1
        private final String elwPblcIstuName;        // 발행사 한글 종목명
        private final String elwPblcMrktPrttNo;      // 발행사코드
        private final String acpr;                   // 행사가
        private final String stckLastTrMonth;        // 최종거래일

        private ElwMasterRow(String mkscShrnIscd, String htsKorIsnm, 
                            String elwNvltOptnClsCode, String elwKoBarrier,
                            String bsktYn, String unasIscd1,
                            String elwPblcIstuName, String elwPblcMrktPrttNo,
                            String acpr, String stckLastTrMonth) {
            this.mkscShrnIscd = mkscShrnIscd;
            this.htsKorIsnm = htsKorIsnm;
            this.elwNvltOptnClsCode = elwNvltOptnClsCode;
            this.elwKoBarrier = elwKoBarrier;
            this.bsktYn = bsktYn;
            this.unasIscd1 = unasIscd1;
            this.elwPblcIstuName = elwPblcIstuName;
            this.elwPblcMrktPrttNo = elwPblcMrktPrttNo;
            this.acpr = acpr;
            this.stckLastTrMonth = stckLastTrMonth;
        }
    }

    private void deleteRecursively(Path root) {
        try {
            if (!Files.exists(root)) {
                return;
            }
            Files.walk(root)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best-effort cleanup
                        }
                    });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
