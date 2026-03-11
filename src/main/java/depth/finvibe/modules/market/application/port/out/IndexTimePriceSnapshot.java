package depth.finvibe.modules.market.application.port.out;

public record IndexTimePriceSnapshot(
        String businessDate,
        String contractHour,
        String businessHour,
        String openPrice,
        String highPrice,
        String lowPrice,
        String currentPrice,
        String previousDayChangeRate,
        String contractVolume,
        String accumulatedTradeAmount
) {
}
