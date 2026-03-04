package depth.finvibe.modules.gamification.infra.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.gamification.application.port.out.UserXpAwardRepository;
import depth.finvibe.modules.gamification.domain.QUserXpAward;
import depth.finvibe.modules.gamification.domain.UserXpAward;

@Repository
@RequiredArgsConstructor
public class UserXpAwardRepositoryImpl implements UserXpAwardRepository {
    private final UserXpAwardJpaRepository userXpAwardJpaRepository;
    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public void save(UserXpAward userXpAward) {
        userXpAwardJpaRepository.save(userXpAward);
    }

    @Override
    public List<UserXpAward> findByUserId(UUID userId) {
        return userXpAwardJpaRepository.findByUserId(userId);
    }

    @Override
    public List<UUID> findTopUsersByTotalXp(int limit) {
        QUserXpAward userXpAward = QUserXpAward.userXpAward;

        return jpaQueryFactory.select(userXpAward.userId)
                .from(userXpAward)
                .groupBy(userXpAward.userId)
                .orderBy(userXpAward.xp.value.sum().desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public List<UserPeriodXp> findUserPeriodXpRankingBetween(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive,
            int limit) {
        QUserXpAward userXpAward = QUserXpAward.userXpAward;

        List<Tuple> rows = jpaQueryFactory
                .select(userXpAward.userId, userXpAward.xp.value.sum())
                .from(userXpAward)
                .where(userXpAward.createdAt.goe(startInclusive)
                        .and(userXpAward.createdAt.lt(endExclusive)))
                .groupBy(userXpAward.userId)
                .orderBy(userXpAward.xp.value.sum().desc(), userXpAward.userId.asc())
                .limit(limit)
                .fetch();

        return rows.stream()
                .map(tuple -> new UserPeriodXp(
                        tuple.get(userXpAward.userId),
                        tuple.get(userXpAward.xp.value.sum())))
                .toList();
    }

    @Override
    public Map<UUID, Long> findUserPeriodXpMapBetween(
            List<UUID> userIds,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        QUserXpAward userXpAward = QUserXpAward.userXpAward;

        List<Tuple> rows = jpaQueryFactory
                .select(userXpAward.userId, userXpAward.xp.value.sum())
                .from(userXpAward)
                .where(userXpAward.userId.in(userIds)
                        .and(userXpAward.createdAt.goe(startInclusive))
                        .and(userXpAward.createdAt.lt(endExclusive)))
                .groupBy(userXpAward.userId)
                .fetch();

        return rows.stream()
                .collect(Collectors.toMap(
                        tuple -> tuple.get(userXpAward.userId),
                        tuple -> tuple.get(userXpAward.xp.value.sum())));
    }
}
