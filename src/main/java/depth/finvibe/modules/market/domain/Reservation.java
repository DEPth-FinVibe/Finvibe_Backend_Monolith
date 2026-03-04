package depth.finvibe.modules.market.domain;

import depth.finvibe.modules.market.domain.enums.ReservationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor(staticName = "create")
@Getter
@Builder
@NoArgsConstructor
public class Reservation {
    private Long tradeId;

    private Long stockId;

    private Long targetPrice;

    private ReservationType type;

    private LocalDateTime createdAt;
}
