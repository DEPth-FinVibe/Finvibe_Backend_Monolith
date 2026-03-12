package depth.finvibe.modules.asset.domain;

import java.math.BigDecimal;

import depth.finvibe.modules.asset.domain.Currency;
import depth.finvibe.modules.asset.domain.error.AssetErrorCode;
import depth.finvibe.common.error.DomainException;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Money {

    private BigDecimal amount;

    private Currency currency;

    public static Money of(Double amount, Currency currency) {
        if(amount == null || currency == null) {
            throw new DomainException(AssetErrorCode.INVALID_MONEY_PARAMS);
        }
        if(amount < 0) {
            throw new DomainException(AssetErrorCode.NEGATIVE_MONEY_AMOUNT);
        }

        return new Money(BigDecimal.valueOf(amount), currency);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        if(amount == null || currency == null) {
            throw new DomainException(AssetErrorCode.INVALID_MONEY_PARAMS);
        }
        if(amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException(AssetErrorCode.NEGATIVE_MONEY_AMOUNT);
        }

        return new Money(amount, currency);
    }

    public Money plus(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new DomainException(AssetErrorCode.CANNOT_ADD_DIFFERENT_CURRENCIES);
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money minus(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new DomainException(AssetErrorCode.CANNOT_SUBTRACT_DIFFERENT_CURRENCIES);
        }

        BigDecimal resultAmount = this.amount.subtract(other.amount);

        if (resultAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException(AssetErrorCode.NEGATIVE_MONEY_AMOUNT);
        }

        return new Money(resultAmount, this.currency);
    }

}
