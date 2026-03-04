package depth.finvibe.common.investment.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
@Data
@Builder
public class ReservationSatisfiedEvent {
    private Long tradeId;
    private String type; // "BUY", "SELL"
    private Long price;
}