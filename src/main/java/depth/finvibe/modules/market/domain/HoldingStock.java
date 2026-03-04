package depth.finvibe.modules.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "holding_stock",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"stock_id", "user_id"})
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class HoldingStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    public static HoldingStock create(Long stockId, UUID userId) {
        return HoldingStock.builder()
                .stockId(stockId)
                .userId(userId)
                .build();
    }
}
