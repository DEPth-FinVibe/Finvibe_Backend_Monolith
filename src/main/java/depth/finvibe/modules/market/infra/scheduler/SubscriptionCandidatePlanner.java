package depth.finvibe.modules.market.infra.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SubscriptionCandidatePlanner {

    enum Tier {
        RESERVATION,
        WATCHER,
        HOLDING
    }

    record Candidate(Long stockId, Tier tier) {
    }

    record CandidatePlan(List<Candidate> candidates, Map<Long, Tier> tierByStockId) {
        List<Long> stockIds() {
            return candidates.stream()
                    .map(Candidate::stockId)
                    .toList();
        }
    }

    CandidatePlan plan(
            List<Long> reservationStockIds,
            List<Long> watcherStockIds,
            List<Long> holdingStockIds,
            Set<Long> currentSubscriptions
    ) {
        Map<Long, Tier> tierByStockId = new LinkedHashMap<>();
        addTier(tierByStockId, reservationStockIds, Tier.RESERVATION);
        addTier(tierByStockId, watcherStockIds, Tier.WATCHER);
        addTier(tierByStockId, holdingStockIds, Tier.HOLDING);

        List<Candidate> candidates = new ArrayList<>(tierByStockId.size());
        for (Tier tier : Tier.values()) {
            currentSubscriptions.stream()
                    .filter(stockId -> tierByStockId.get(stockId) == tier)
                    .sorted()
                    .map(stockId -> new Candidate(stockId, tier))
                    .forEach(candidates::add);

            tierByStockId.entrySet().stream()
                    .filter(entry -> entry.getValue() == tier)
                    .map(Map.Entry::getKey)
                    .filter(stockId -> !currentSubscriptions.contains(stockId))
                    .sorted(Comparator.naturalOrder())
                    .map(stockId -> new Candidate(stockId, tier))
                    .forEach(candidates::add);
        }

        return new CandidatePlan(List.copyOf(candidates), Map.copyOf(tierByStockId));
    }

    Long findLowerTierEviction(
            LinkedHashSet<Long> subscriptionOrder,
            Map<Long, Tier> tierByStockId,
            Tier incomingTier
    ) {
        List<Long> subscriptions = new ArrayList<>(subscriptionOrder);
        for (int index = subscriptions.size() - 1; index >= 0; index--) {
            Long stockId = subscriptions.get(index);
            Tier currentTier = tierByStockId.get(stockId);
            if (currentTier == null || currentTier.ordinal() > incomingTier.ordinal()) {
                return stockId;
            }
        }
        return null;
    }

    Long findQuotaEviction(LinkedHashSet<Long> subscriptionOrder, Map<Long, Tier> tierByStockId) {
        List<Long> subscriptions = new ArrayList<>(subscriptionOrder);
        Long selected = null;
        int selectedTier = Integer.MIN_VALUE;

        for (int index = subscriptions.size() - 1; index >= 0; index--) {
            Long stockId = subscriptions.get(index);
            Tier tier = tierByStockId.get(stockId);
            int tierOrder = tier == null ? Integer.MAX_VALUE : tier.ordinal();
            if (tierOrder > selectedTier) {
                selected = stockId;
                selectedTier = tierOrder;
            }
        }
        return selected;
    }

    private void addTier(Map<Long, Tier> tierByStockId, List<Long> stockIds, Tier tier) {
        if (stockIds == null) {
            return;
        }
        stockIds.stream()
                .filter(stockId -> stockId != null)
                .forEach(stockId -> tierByStockId.putIfAbsent(stockId, tier));
    }
}
