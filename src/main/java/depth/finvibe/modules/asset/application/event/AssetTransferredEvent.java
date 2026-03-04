package depth.finvibe.modules.asset.application.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetTransferredEvent {
  private Long sourcePortfolioId;
  private Long targetPortfolioId;
  private Long stockId;
  private boolean merged;
}
