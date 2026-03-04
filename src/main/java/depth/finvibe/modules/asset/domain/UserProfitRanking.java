package depth.finvibe.modules.asset.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.asset.domain.enums.UserProfitRankType;

@Entity
@Table(
        name = "user_profit_ranking",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"rank_type", "user_id"})
        },
        indexes = {
                @Index(name = "idx_user_profit_ranking_type_rank", columnList = "rank_type,rank"),
                @Index(name = "idx_user_profit_ranking_type_user_id", columnList = "rank_type,user_id")
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserProfitRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private String userNickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "rank_type", nullable = false)
    private UserProfitRankType rankType;

    @Column(name = "total_return_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal totalReturnRate;

    @Column(name = "total_profit_loss", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalProfitLoss;

    @Column(name = "rank", nullable = false)
    private Integer rank;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static UserProfitRanking create(
            UUID userId,
            String userNickname,
            UserProfitRankType rankType,
            BigDecimal totalReturnRate,
            BigDecimal totalProfitLoss,
            Integer rank
    ) {
        return UserProfitRanking.builder()
                .userId(userId)
                .userNickname(userNickname)
                .rankType(rankType)
                .totalReturnRate(totalReturnRate)
                .totalProfitLoss(totalProfitLoss)
                .rank(rank)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
