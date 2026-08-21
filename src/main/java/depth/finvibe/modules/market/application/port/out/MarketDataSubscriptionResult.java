package depth.finvibe.modules.market.application.port.out;

public enum MarketDataSubscriptionResult {
    SUBSCRIBED,
    ALREADY_SUBSCRIBED,
    NO_SESSION,
    NO_CAPACITY,
    SEND_FAILED;

    public boolean isSuccess() {
        return this == SUBSCRIBED || this == ALREADY_SUBSCRIBED;
    }
}
