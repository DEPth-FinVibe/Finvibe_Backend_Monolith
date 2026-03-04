package depth.finvibe.modules.market.domain;

import depth.finvibe.modules.market.domain.enums.RankType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "stock_ranking",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"stock_id", "rank_type"})
        },
        indexes = {
                @Index(name = "idx_ranking_type_rank", columnList = "rank_type,rank")
        }
)
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@EqualsAndHashCode
public class StockRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rank_type", nullable = false)
    private RankType rankType;

    @Column(name = "rank", nullable = false)
    private Integer rank;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static StockRanking create(Long stockId, RankType rankType, Integer rank) {
        return StockRanking.builder()
                .stockId(stockId)
                .rankType(rankType)
                .rank(rank)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
