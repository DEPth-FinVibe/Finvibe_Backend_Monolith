package depth.finvibe.modules.gamification.infra.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.gamification.domain.UserXpAward;

public interface UserXpAwardJpaRepository extends JpaRepository<UserXpAward, Long> {
    List<UserXpAward> findByUserId(UUID userId);
}
