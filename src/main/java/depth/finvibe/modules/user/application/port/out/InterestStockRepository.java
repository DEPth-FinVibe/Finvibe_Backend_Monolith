package depth.finvibe.modules.user.application.port.out;

import depth.finvibe.modules.user.domain.InterestStock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterestStockRepository {
    InterestStock save(InterestStock interestStock);
    
    void deleteByUserIdAndStockId(Long userId, Long stockId);
    
    List<InterestStock> findAllByUserId(Long userId);
    
    Optional<InterestStock> findByUserIdAndStockId(Long userId, Long stockId);
}
