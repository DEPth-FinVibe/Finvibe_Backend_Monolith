package depth.finvibe.modules.market.infra.redis;

public final class MarketRedisPubSubTopic {

    public static final String CURRENT_PRICE_UPDATED = "market:price-updated";

    public static String resolveCurrentPriceUpdatedChannel(String baseTopic, Long stockId, int partitionCount) {
		return resolveCurrentPriceUpdatedChannel(baseTopic, stockId, partitionCount, "classic");
	}

    public static String resolveCurrentPriceUpdatedChannel(String baseTopic, Long stockId, int partitionCount, String mode) {
        if (baseTopic == null || baseTopic.isBlank()) {
            baseTopic = CURRENT_PRICE_UPDATED;
        }

        if (partitionCount <= 1 || stockId == null) {
            return baseTopic;
        }

        int partition = Math.floorMod(stockId.hashCode(), partitionCount);
		if ("sharded".equalsIgnoreCase(mode)) {
			return baseTopic + ":{" + partition + "}";
		}
		return baseTopic + ":" + partition;
    }

    private MarketRedisPubSubTopic() {
    }
}
