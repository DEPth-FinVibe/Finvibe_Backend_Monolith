package depth.finvibe.common.investment.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardBadgeEvent {
  private String userId;
  private String badgeCode;
  private Instant issuedAt;
  private String reason;
}
