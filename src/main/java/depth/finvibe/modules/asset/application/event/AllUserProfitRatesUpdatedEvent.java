package depth.finvibe.modules.asset.application.event;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.asset.application.port.out.UserProfitRankingData;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllUserProfitRatesUpdatedEvent {
  private List<UserProfitRankingData> rankings;
  private LocalDateTime calculatedAt;
}
