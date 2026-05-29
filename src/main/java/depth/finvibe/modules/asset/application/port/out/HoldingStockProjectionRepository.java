package depth.finvibe.modules.asset.application.port.out;

import java.util.Set;

public interface HoldingStockProjectionRepository {
	void replaceHoldingStockIds(Set<Long> stockIds);

	boolean isEmpty();
}
