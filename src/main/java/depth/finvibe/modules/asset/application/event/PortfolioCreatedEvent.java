package depth.finvibe.modules.asset.application.event;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioCreatedEvent {
	private Long portfolioId;
	private UUID userId;
}
