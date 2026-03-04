package depth.finvibe.modules.trade.application.port.out;

public interface MarketClient {
    boolean isMarketOpen();

    Long getCurrentPrice(Long stockId);

    String getStockNameById(Long stockId);
}
