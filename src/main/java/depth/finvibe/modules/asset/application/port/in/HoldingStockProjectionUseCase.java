package depth.finvibe.modules.asset.application.port.in;

public interface HoldingStockProjectionUseCase {
	void rebuild();

	void rebuildIfEmpty();
}
