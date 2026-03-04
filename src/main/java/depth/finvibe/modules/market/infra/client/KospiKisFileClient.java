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
public class KospiKisFileClient implements KisFileClient {

    private static final String KOSPI_ZIP_URL = "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip";
    private static final String KOSPI_MST_NAME = "kospi_code.mst";
    private static final Charset KIS_CHARSET = Charset.forName("MS949"); // cp949
    private static final int PART2_TOTAL_WIDTH = 228;

    @Override
    public List<StockDto.RealMarketStockResponse> fetchStocksInKisFile() {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("kis-kospi-");
            Path zipPath = tempDir.resolve("kospi_code.zip");
            downloadFile(KOSPI_ZIP_URL, zipPath);
            Path mstPath = unzipToFile(zipPath, tempDir, KOSPI_MST_NAME);
            return parseKospiFile(mstPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load KOSPI master file", e);
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

    private List<StockDto.RealMarketStockResponse> parseKospiFile(Path mstPath) throws IOException {
        List<StockDto.RealMarketStockResponse> result = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(mstPath, KIS_CHARSET)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() <= PART2_TOTAL_WIDTH) {
                    continue;
                }
                int part1End = line.length() - PART2_TOTAL_WIDTH;
                String part1 = line.substring(0, part1End);
                String part2 = line.substring(part1End);

                KospiMasterRow row = parseMasterRow(part1, part2);
                String typeCode = selectNonZeroCode(
                        row.bstpSmalDivCode,
                        row.bstpMedmDivCode,
                        row.bstpLargDivCode
                );

                result.add(StockDto.RealMarketStockResponse.builder()
                        .symbol(row.mkscShrnIscd)
                        .name(row.htsKorIsnm)
                        .typeCode(typeCode)
                        .build());
            }
        }
        return result;
    }

    private KospiMasterRow parseMasterRow(String part1, String part2) {
        String symbol = rstrip(part1.substring(0, Math.min(9, part1.length())));
        String name = rstrip(part1.substring(Math.min(21, part1.length())));
        
        // Parse only necessary fields from part2
        int offset = 2 + 1; // skip scrtGrpClsCode(2) + avlsScalClsCode(1)
        String bstpLargDivCode = part2.substring(offset, Math.min(part2.length(), offset + 4)).trim();
        offset += 4;
        String bstpMedmDivCode = part2.substring(offset, Math.min(part2.length(), offset + 4)).trim();
        offset += 4;
        String bstpSmalDivCode = part2.substring(offset, Math.min(part2.length(), offset + 4)).trim();
        
        return new KospiMasterRow(symbol, name, bstpLargDivCode, bstpMedmDivCode, bstpSmalDivCode);
    }



    private String selectNonZeroCode(String... codes) {
        for (String code : codes) {
            if (code != null && !code.isBlank() && !"0000".equals(code)) {
                return code;
            }
        }

        log.warn("All codes are zero or blank: {}", String.join(", ", codes));
        return "";
    }

    private String rstrip(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static class KospiMasterRow {
        private final String mkscShrnIscd;
        private final String htsKorIsnm;
        private final String bstpLargDivCode;
        private final String bstpMedmDivCode;
        private final String bstpSmalDivCode;

        private KospiMasterRow(String mkscShrnIscd, String htsKorIsnm, 
                              String bstpLargDivCode, String bstpMedmDivCode, String bstpSmalDivCode) {
            this.mkscShrnIscd = mkscShrnIscd;
            this.htsKorIsnm = htsKorIsnm;
            this.bstpLargDivCode = bstpLargDivCode;
            this.bstpMedmDivCode = bstpMedmDivCode;
            this.bstpSmalDivCode = bstpSmalDivCode;
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
