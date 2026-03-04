package depth.finvibe.modules.asset.application;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import depth.finvibe.modules.asset.application.event.AllUserProfitRatesUpdatedEvent;
import depth.finvibe.modules.asset.application.port.out.UserProfitRankingData;
import depth.finvibe.modules.asset.application.port.out.UserProfitRankingRepository;
import depth.finvibe.modules.asset.domain.enums.UserProfitRankType;

@Service
@RequiredArgsConstructor
public class UserProfitRankingEventService {
  private final UserProfitRankingRepository userProfitRankingRepository;

  @EventListener
  public void handleAllUserProfitRatesUpdatedEvent(AllUserProfitRatesUpdatedEvent event) {
    if (event == null) {
      return;
    }

    List<UserProfitRankingData> rankings = event.getRankings();
    if (rankings == null) {
      userProfitRankingRepository.replaceAllRankings(UserProfitRankType.DAILY, List.of());
      return;
    }

    userProfitRankingRepository.replaceAllRankings(UserProfitRankType.DAILY, rankings);
  }
}
