package depth.finvibe.modules.user.infra.persistence;

import depth.finvibe.modules.user.application.port.out.InterestStockRepository;
import depth.finvibe.modules.user.domain.InterestStock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class InterestStockRepositoryImpl implements InterestStockRepository {

    private final JpaInterestStockRepository jpaInterestStockRepository;

    @Override
    public InterestStock save(InterestStock interestStock) {
        return jpaInterestStockRepository.save(interestStock);
    }

    @Override
    public void deleteByUserIdAndStockId(Long userId, Long stockId) {
        jpaInterestStockRepository.deleteByUserIdAndStockId(userId, stockId);
    }

    @Override
    public List<InterestStock> findAllByUserId(Long userId) {
        return jpaInterestStockRepository.findAllByUserId(userId);
    }

    @Override
    public Optional<InterestStock> findByUserIdAndStockId(Long userId, Long stockId) {
        return jpaInterestStockRepository.findByUserIdAndStockId(userId, stockId);
    }
}
