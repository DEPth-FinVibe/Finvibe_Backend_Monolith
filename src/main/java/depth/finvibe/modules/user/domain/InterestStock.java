package depth.finvibe.modules.user.domain;

import depth.finvibe.modules.user.domain.enums.UserRole;
import depth.finvibe.modules.user.domain.error.UserErrorCode;
import depth.finvibe.common.user.domain.TimeStampedBaseEntity;
import depth.finvibe.common.user.error.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(InterestStockId.class)
public class InterestStock extends TimeStampedBaseEntity {

    @Id
    @Column(nullable = false)
    private UUID userId;

    @Id
    @Column(nullable = false)
    private Long stockId;

    @Column(nullable = false)
    private String stockName;

    public static InterestStock create(UUID userId, Long stockId, String stockName) {
        return InterestStock.builder()
                .userId(userId)
                .stockId(stockId)
                .stockName(stockName)
                .build();
    }

    public void validateCreatable(UUID requesterId, UserRole role) {
        if (!this.userId.equals(requesterId) && role != UserRole.ADMIN) {
            throw new DomainException(UserErrorCode.UNAUTHORIZED_INTEREST_STOCK_CREATION);
        }
    }

    public void validateDeletable(UUID requesterId, UserRole role) {
        if (!this.userId.equals(requesterId) && role != UserRole.ADMIN) {
            throw new DomainException(UserErrorCode.UNAUTHORIZED_INTEREST_STOCK_DELETION);
        }
    }
}
