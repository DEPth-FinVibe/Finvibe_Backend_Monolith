package depth.finvibe.modules.market.application.port.out;

import java.util.Map;

public interface StockThemeRepository {
    Map<String, String> findSymbolToThemeMap();
}
