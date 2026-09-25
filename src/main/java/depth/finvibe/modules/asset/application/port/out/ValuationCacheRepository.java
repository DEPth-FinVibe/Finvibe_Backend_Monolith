package depth.finvibe.modules.asset.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import depth.finvibe.modules.asset.dto.PortfolioValuationDto.PortfolioSnapshot;
import depth.finvibe.modules.asset.dto.PortfolioValuationDto.UserSnapshot;

public interface ValuationCacheRepository {
    Optional<UserSnapshot> findUser(String userId);

    Map<Long, PortfolioSnapshot> findPortfolios(List<Long> portfolioIds);

    void cacheUserIfNewer(UserSnapshot snapshot);

    void cachePortfolioIfNewer(PortfolioSnapshot snapshot);
}
