package depth.finvibe.modules.asset.application.event;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioDeletedEvent {
	private Long deletedPortfolioId;
	private Long defaultPortfolioId;
	private UUID userId;
	private List<Long> stockIds;
}
