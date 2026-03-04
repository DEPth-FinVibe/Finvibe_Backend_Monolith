package depth.finvibe.modules.asset.infra.persistence;

import depth.finvibe.modules.asset.domain.PortfolioGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PortfolioGroupJpaRepository extends JpaRepository<PortfolioGroup, Long> {
    List<PortfolioGroup> findAllByUserId(UUID userId);

    boolean existsByIdAndUserId(Long portfolioId, UUID userId);
}
