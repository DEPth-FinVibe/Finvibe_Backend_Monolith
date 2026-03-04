package depth.finvibe.modules.gamification.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.gamification.application.port.out.UserXpRepository;
import depth.finvibe.modules.gamification.domain.UserXp;

@Repository
@RequiredArgsConstructor
public class UserXpRepositoryImpl implements UserXpRepository {
    private final UserXpJpaRepository userXpJpaRepository;

    @Override
    public void save(UserXp userXp) {
        userXpJpaRepository.save(userXp);
    }

    @Override
    public void saveAll(List<UserXp> userXps) {
        userXpJpaRepository.saveAll(userXps);
    }

    @Override
    public List<UserXp> findAll() {
        return userXpJpaRepository.findAll();
    }

    @Override
    public Optional<UserXp> findByUserId(UUID userId) {
        return userXpJpaRepository.findByUserId(userId);
    }

    @Override
    public List<UserXp> findAllByUserIdInOrderByWeeklyXpDesc(List<UUID> userIds) {
        return userXpJpaRepository.findAllByUserIdInOrderByWeeklyXpDesc(userIds);
    }

    @Override
    public List<UserXp> findAllByUserIdIn(List<UUID> userIds) {
        return userXpJpaRepository.findAllByUserIdIn(userIds);
    }
}
