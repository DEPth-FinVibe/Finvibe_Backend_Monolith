package depth.finvibe.modules.asset.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.asset.domain.UserProfitRanking;
import depth.finvibe.modules.asset.domain.enums.UserProfitRankType;

public interface UserProfitRankingJpaRepository extends JpaRepository<UserProfitRanking, Long> {
  void deleteByRankType(UserProfitRankType rankType);

  Optional<UserProfitRanking> findByRankTypeAndUserId(UserProfitRankType rankType, UUID userId);

  List<UserProfitRanking> findByRankTypeOrderByRankAsc(UserProfitRankType rankType);

  Page<UserProfitRanking> findByRankTypeOrderByRankAsc(UserProfitRankType rankType, Pageable pageable);
}
