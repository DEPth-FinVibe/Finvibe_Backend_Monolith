package depth.finvibe.modules.asset.application.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioUpdatedEvent {
    private Long portfolioId;
    private Long userId;
}
