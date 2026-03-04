package depth.finvibe.modules.market.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor(staticName = "create")
@Getter
@Builder
@EqualsAndHashCode
public class CurrentStockWatcher {
    private final Long stockId;
    private final UUID watcherId;
}
