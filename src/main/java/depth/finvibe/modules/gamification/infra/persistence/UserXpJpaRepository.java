package depth.finvibe.modules.gamification.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.gamification.domain.UserXp;

public interface UserXpJpaRepository extends JpaRepository<UserXp, Long> {
    Optional<UserXp> findByUserId(Long userId);

    List<UserXp> findAllByUserIdInOrderByWeeklyXpDesc(List<Long> userIds);

    List<UserXp> findAllByUserIdIn(List<Long> userIds);
}
