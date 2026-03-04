package depth.finvibe.modules.wallet.infra.persistence;

import depth.finvibe.modules.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletJpaRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
