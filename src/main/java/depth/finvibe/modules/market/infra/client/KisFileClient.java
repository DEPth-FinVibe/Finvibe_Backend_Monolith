package depth.finvibe.modules.market.infra.client;

import depth.finvibe.modules.market.dto.StockDto;

import java.util.List;

/**
 * KIS 파일 클라이언트
 * KIS에서 제공하는 주식 관련 파일을 다운로드 및 파싱
 */
public interface KisFileClient {
    List<StockDto.RealMarketStockResponse> fetchStocksInKisFile();
}
