package depth.finvibe.modules.wallet.domain;

import depth.finvibe.common.investment.domain.TimeStampedBaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Wallet extends TimeStampedBaseEntity {
    private static final Long WALLET_INITIAL_BALANCE = 10000000L; // 1000만원

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID userId;

    @Embedded
    private Money balance;

    public void deposit(Money amount) {
        this.balance = this.balance.plus(amount);
    }

    public void withdraw(Money amount) {
        this.balance = this.balance.minus(amount);
    }

    public static Wallet create(UUID userId) {
        return new Wallet(null, userId, new Money(WALLET_INITIAL_BALANCE));
    }
}
