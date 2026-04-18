package depth.finvibe.modules.asset.application;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import depth.finvibe.modules.asset.application.event.AssetTransferredEvent;
import depth.finvibe.modules.asset.application.port.in.AssetCommandUseCase;
import depth.finvibe.modules.asset.application.port.in.AssetEventUseCase;
import depth.finvibe.modules.asset.application.port.out.PortfolioGroupRepository;
import depth.finvibe.modules.asset.domain.Currency;
import depth.finvibe.modules.asset.domain.PortfolioGroup;
import depth.finvibe.modules.asset.dto.PortfolioGroupDto;
import depth.finvibe.common.investment.dto.SignUpEvent;
import depth.finvibe.common.investment.dto.TradeExecutedEvent;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssetEventService implements AssetEventUseCase {
    private final AssetCommandUseCase commandUseCase;
    private final PortfolioGroupRepository portfolioGroupRepository;

    @Transactional
    public void handleTradeExecutedEvent(TradeExecutedEvent event) {

        Long portfolioId = event.getPortfolioId();
        UUID userId = UUID.fromString(event.getUserId());

        if (event.getType().equals("BUY")) {
            PortfolioGroupDto.RegisterAssetRequest request = createRegisterRequestFrom(event);
            commandUseCase.registerAsset(portfolioId,request, userId);
        } else if (event.getType().equals("SELL")) {
            PortfolioGroupDto.UnregisterAssetRequest request = createUnregisterRequestFrom(event);
            commandUseCase.unregisterAsset(portfolioId, request, userId);
        } else {
            log.warn("Ignoring trade event of type: {}", event.getType());
        }

    }

    @Transactional
    public void handleSignUpEvent(SignUpEvent event) {
        UUID userId = UUID.fromString(event.getUserId());
        commandUseCase.createDefaultPortfolioGroup(userId);
    }

    private PortfolioGroupDto.RegisterAssetRequest createRegisterRequestFrom(TradeExecutedEvent event) {
        return PortfolioGroupDto.RegisterAssetRequest.builder()
                .stockId(event.getStockId())
                .name(event.getName())
                .stockPrice(BigDecimal.valueOf(event.getPrice()))
                .amount(event.getAmount())
                .currency(Currency.valueOf(event.getCurrency()))
                .build();
    }

    private PortfolioGroupDto.UnregisterAssetRequest createUnregisterRequestFrom(TradeExecutedEvent event) {
        return PortfolioGroupDto.UnregisterAssetRequest.builder()
                .stockId(event.getStockId())
                .stockPrice(BigDecimal.valueOf(event.getPrice()))
                .amount(event.getAmount())
                .currency(Currency.valueOf(event.getCurrency()))
                .build();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    @Async
    public void handleAssetTransferredEvent(AssetTransferredEvent event) {
        log.info("Handling asset transferred event. sourcePortfolioId={}, targetPortfolioId={}, stockId={}, merged={}",
                event.getSourcePortfolioId(), event.getTargetPortfolioId(), event.getStockId(), event.isMerged());

        try {
            portfolioGroupRepository.findByIdWithAssets(event.getSourcePortfolioId())
                    .ifPresent(PortfolioGroup::recalculateValuation);
            portfolioGroupRepository.findByIdWithAssets(event.getTargetPortfolioId())
                    .ifPresent(PortfolioGroup::recalculateValuation);
            log.info("Successfully recalculated valuations for source and target portfolios.");
        } catch (Exception e) {
            log.error("Failed to recalculate valuation after asset transfer. sourcePortfolioId={}, targetPortfolioId={}",
                    event.getSourcePortfolioId(), event.getTargetPortfolioId(), e);
            // 실패해도 예외를 던지지 않음 - 다음 배치에서 재계산됨
        }
    }
}
