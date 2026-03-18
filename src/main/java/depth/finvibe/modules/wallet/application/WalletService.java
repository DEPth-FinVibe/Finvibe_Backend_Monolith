package depth.finvibe.modules.wallet.application;

import depth.finvibe.modules.wallet.application.port.in.WalletCommandUseCase;
import depth.finvibe.modules.wallet.application.port.in.WalletQueryUseCase;
import depth.finvibe.modules.wallet.application.port.out.WalletRepository;
import depth.finvibe.modules.wallet.domain.Wallet;
import depth.finvibe.modules.wallet.domain.Money;
import depth.finvibe.modules.wallet.domain.error.WalletErrorCode;
import depth.finvibe.modules.wallet.dto.WalletDto;
import depth.finvibe.common.error.DomainException;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService implements WalletCommandUseCase, WalletQueryUseCase {
    private final WalletRepository walletRepository;
    private final MeterRegistry meterRegistry;

    @Transactional
    public WalletDto.WalletResponse createWallet(UUID userId) {
        if (userId == null) {
            throw new DomainException(WalletErrorCode.INVALID_USER_ID);
        }

        Wallet wallet = Wallet.create(userId);
        Wallet saved = walletRepository.save(wallet);
        recordBalanceMutation("signup_seed", saved.getBalance().getPrice());

        return WalletDto.WalletResponse.from(saved);
    }

    @Transactional
    public WalletDto.WalletResponse deposit(UUID userId, Long price) {
        Wallet wallet = findWallet(userId);

        validatePrice(price);
        wallet.deposit(new Money(price));
        recordBalanceMutation("deposit", price);

        return WalletDto.WalletResponse.from(wallet);
    }

    @Transactional
    public WalletDto.WalletResponse withdraw(UUID userId, Long price) {
        Wallet wallet = findWallet(userId);

        validatePrice(price);
        try {
            wallet.withdraw(new Money(price));
            recordBalanceMutation("withdraw", price);
            return WalletDto.WalletResponse.from(wallet);
        } catch (DomainException ex) {
            if (WalletErrorCode.INSUFFICIENT_BALANCE.getCode().equals(ex.getErrorCode().getCode())) {
                meterRegistry.counter("wallet.insufficient.balance").increment();
            }
            throw ex;
        }
    }

    private void validatePrice(Long price) {
        if (price == null || price <= 0) {
            throw new DomainException(WalletErrorCode.INVALID_MONEY_PRICE);
        }
    }

    @Override
    public WalletDto.WalletResponse getWalletByUserId(UUID userId) {
        Wallet wallet = findWallet(userId);

        return WalletDto.WalletResponse.from(wallet);
    }

    private Wallet findWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    meterRegistry.counter("wallet.wallet.not_found").increment();
                    return new DomainException(WalletErrorCode.WALLET_NOT_FOUND);
                });
    }

    private void recordBalanceMutation(String type, Long amount) {
        meterRegistry.counter("wallet.balance.mutations", "type", type).increment();
        DistributionSummary.builder("wallet.balance.mutation.amount")
                .description("지갑 잔고 변동 금액")
                .baseUnit("won")
                .tag("type", type)
                .register(meterRegistry)
                .record(amount);
    }
}
