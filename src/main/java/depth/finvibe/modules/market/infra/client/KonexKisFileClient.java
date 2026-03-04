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
public class KonexKisFileClient implements KisFileClient {

    private static final String KONEX_ZIP_URL = "https://new.real.download.dws.co.kr/common/master/konex_code.mst.zip";
    private static final String KONEX_MST_NAME = "konex_code.mst";
    private static final Charset KIS_CHARSET = Charset.forName("MS949"); // cp949

    @Override
    public List<StockDto.RealMarketStockResponse> fetchStocksInKisFile() {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("kis-konex-");
            Path zipPath = tempDir.resolve("konex_code.zip");
            downloadFile(KONEX_ZIP_URL, zipPath);
            Path mstPath = unzipToFile(zipPath, tempDir, KONEX_MST_NAME);
            return parseKonexFile(mstPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load KONEX master file", e);
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

    private List<StockDto.RealMarketStockResponse> parseKonexFile(Path mstPath) throws IOException {
        List<StockDto.RealMarketStockResponse> result = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(mstPath, KIS_CHARSET)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty()) {
                    continue;
                }

                KonexMasterRow row = parseMasterRow(line);
                
                result.add(StockDto.RealMarketStockResponse.builder()
                        .symbol(row.mkscShrnIscd)
                        .name(row.htsKorIsnm)
                        .typeCode(row.scrtGrpClsCode)
                        .build());
            }
        }
        return result;
    }

    private KonexMasterRow parseMasterRow(String row) {
        // 파이썬 파싱 로직에 따른 필드 위치
        // mksc_shrn_iscd = row[0:9]
        String mkscShrnIscd = safeSubstring(row, 0, 9).trim();
        
        // stnd_iscd = row[9:21]
        // String stndIscd = safeSubstring(row, 9, 21).trim();
        
        // scrt_grp_cls_code = row[-184:-182]
        String scrtGrpClsCode = safeSubstring(row, row.length() - 184, row.length() - 182).trim();
        
        // hts_kor_isnm = row[21:-184]
        String htsKorIsnm = safeSubstring(row, 21, row.length() - 184).trim();
        
        return new KonexMasterRow(mkscShrnIscd, htsKorIsnm, scrtGrpClsCode);
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

    private static class KonexMasterRow {
        private final String mkscShrnIscd;
        private final String htsKorIsnm;
        private final String scrtGrpClsCode;

        private KonexMasterRow(String mkscShrnIscd, String htsKorIsnm, String scrtGrpClsCode) {
            this.mkscShrnIscd = mkscShrnIscd;
            this.htsKorIsnm = htsKorIsnm;
            this.scrtGrpClsCode = scrtGrpClsCode;
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
