package depth.finvibe.common.investment.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchPriceUpdatedEvent {
    private LocalDateTime batchExecutedAt;
    private Integer totalStockCount;
    private List<Long> updatedStockIds;
}
