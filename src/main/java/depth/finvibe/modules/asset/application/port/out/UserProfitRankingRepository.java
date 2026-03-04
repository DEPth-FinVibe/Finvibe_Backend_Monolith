package depth.finvibe.modules.asset.application.port.out;

import java.util.List;

import depth.finvibe.modules.asset.domain.enums.UserProfitRankType;

public interface UserProfitRankingRepository {
  void replaceAllRankings(UserProfitRankType rankType, List<UserProfitRankingData> rankings);
}
