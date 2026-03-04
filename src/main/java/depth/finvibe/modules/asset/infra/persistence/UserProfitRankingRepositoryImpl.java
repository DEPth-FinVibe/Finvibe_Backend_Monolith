package depth.finvibe.modules.asset.infra.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.asset.application.port.out.UserProfitRankingData;
import depth.finvibe.modules.asset.application.port.out.UserProfitRankingRepository;
import depth.finvibe.modules.asset.domain.UserProfitRanking;
import depth.finvibe.modules.asset.domain.enums.UserProfitRankType;
import lombok.RequiredArgsConstructor;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserProfitRankingRepositoryImpl implements UserProfitRankingRepository {
  private final UserProfitRankingJpaRepository jpaRepository;

  @Override
  @Transactional
  public void replaceAllRankings(UserProfitRankType rankType, List<UserProfitRankingData> rankings) {
    if (rankType == null) {
      return;
    }

    if (rankings == null || rankings.isEmpty()) {
      jpaRepository.deleteByRankType(rankType);
      return;
    }

    // 1. 기존 데이터 전체 삭제
    jpaRepository.deleteByRankType(rankType);

    jpaRepository.flush();

    // 2. 정렬: totalReturnRate 내림차순, 동점일 경우 totalProfitLoss 내림차순
    List<UserProfitRankingData> sortedRankings = rankings.stream()
      .sorted(
        Comparator.comparing(UserProfitRankingData::totalReturnRate, Comparator.reverseOrder())
          .thenComparing(UserProfitRankingData::totalProfitLoss, Comparator.reverseOrder())
      )
      .toList();

    // 3. Rank 부여 및 Entity 생성
    List<UserProfitRanking> entities = new ArrayList<>();
    for (int i = 0; i < sortedRankings.size(); i++) {
      UserProfitRankingData data = sortedRankings.get(i);
      entities.add(UserProfitRanking.create(
        data.userId(),
        data.userNickname(),
        rankType,
        data.totalReturnRate(),
        data.totalProfitLoss(),
        i + 1  // rank는 1부터 시작
      ));
    }

    // 4. 배치 저장
    jpaRepository.saveAll(entities);

    log.info("Replaced all user profit rankings for rank type {}: {} entries saved.", rankType, entities.size());
  }
}
