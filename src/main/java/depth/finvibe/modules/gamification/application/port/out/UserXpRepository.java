package depth.finvibe.modules.gamification.application.port.out;

import depth.finvibe.modules.gamification.domain.UserXp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserXpRepository {
    void save(UserXp userXp);
    void saveAll(List<UserXp> userXps);
    List<UserXp> findAll();
    Optional<UserXp> findByUserId(UUID userId);
    List<UserXp> findAllByUserIdInOrderByWeeklyXpDesc(List<UUID> userIds);
    List<UserXp> findAllByUserIdIn(List<UUID> userIds);
}
