package depth.finvibe.common.investment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchPriceSnapshot {
    private Long stockId;
    private BigDecimal price;
    private LocalDateTime at;
}
