package depth.finvibe.modules.asset.infra.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.asset.application.port.out.UserProfitRankingQueryRepository;
import depth.finvibe.modules.asset.domain.UserProfitRanking;
import depth.finvibe.modules.asset.domain.enums.UserProfitRankType;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserProfitRankingQueryRepositoryImpl implements UserProfitRankingQueryRepository {
  private final UserProfitRankingJpaRepository jpaRepository;

  @Override
  public Page<UserProfitRanking> findByRankType(UserProfitRankType rankType, Pageable pageable) {
    return jpaRepository.findByRankTypeOrderByRankAsc(rankType, pageable);
  }
}
