package depth.finvibe.modules.user.infra.persistence;

import depth.finvibe.modules.user.domain.InterestStock;
import depth.finvibe.modules.user.domain.InterestStockId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaInterestStockRepository extends JpaRepository<InterestStock, InterestStockId> {
    void deleteByUserIdAndStockId(UUID userId, Long stockId);
    List<InterestStock> findAllByUserId(UUID userId);
    Optional<InterestStock> findByUserIdAndStockId(UUID userId, Long stockId);
}
