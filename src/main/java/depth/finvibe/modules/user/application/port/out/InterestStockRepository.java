package depth.finvibe.modules.user.application.port.out;

import depth.finvibe.modules.user.domain.InterestStock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterestStockRepository {
    InterestStock save(InterestStock interestStock);
    
    void deleteByUserIdAndStockId(UUID userId, Long stockId);
    
    List<InterestStock> findAllByUserId(UUID userId);
    
    Optional<InterestStock> findByUserIdAndStockId(UUID userId, Long stockId);
}
