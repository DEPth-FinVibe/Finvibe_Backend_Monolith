package depth.finvibe.modules.wallet.domain;

import depth.finvibe.modules.wallet.domain.error.WalletErrorCode;
import depth.finvibe.common.error.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor
@Getter
public class Money {

  @Column(name = "balance")
  private Long price;

  public Money(Long price) {
    if (price == null || price < 0) {
      throw new DomainException(WalletErrorCode.INVALID_MONEY_PRICE);
    }

    this.price = price;
  }

  public Money plus(Money other) {
    return new Money(this.price + other.price);
  }

  public Money minus(Money other) {
    if (this.price < other.price) {
      throw new DomainException(WalletErrorCode.INSUFFICIENT_BALANCE);
    }

    return new Money(this.price - other.price);
  }

}
