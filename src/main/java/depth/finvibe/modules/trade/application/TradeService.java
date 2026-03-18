package depth.finvibe.modules.trade.application;

import java.time.Instant;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.user.domain.enums.UserRole;
import depth.finvibe.modules.trade.application.port.in.TradeCommandUseCase;
import depth.finvibe.modules.trade.application.port.in.TradeQueryUseCase;
import depth.finvibe.modules.trade.application.port.out.AssetClient;
import depth.finvibe.modules.trade.application.port.out.MarketClient;
import depth.finvibe.modules.trade.application.port.out.TradeEventProducer;
import depth.finvibe.modules.trade.application.port.out.TradeRepository;
import depth.finvibe.modules.trade.application.port.out.WalletClient;
import depth.finvibe.modules.trade.domain.Trade;
import depth.finvibe.modules.trade.domain.enums.TradeType;
import depth.finvibe.modules.trade.domain.enums.TransactionType;
import depth.finvibe.modules.trade.domain.error.TradeErrorCode;
import depth.finvibe.modules.trade.dto.TradeDto;
import depth.finvibe.modules.trade.dto.TradeOrderType;
import depth.finvibe.common.investment.application.port.out.GamificationEventProducer;
import depth.finvibe.common.investment.dto.MetricEventType;
import depth.finvibe.common.investment.dto.UserMetricUpdatedEvent;
import depth.finvibe.common.error.DomainException;
@Service
@RequiredArgsConstructor
public class TradeService implements TradeCommandUseCase, TradeQueryUseCase {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TradeRepository tradeRepository;
    private final TradeEventProducer tradeEventProducer;
    private final GamificationEventProducer gamificationEventProducer;
    private final AssetClient assetClient;
    private final MarketClient marketClient;
    private final WalletClient walletClient;
    private final MeterRegistry meterRegistry;


    @Transactional()
    public TradeDto.TradeResponse findTrade(Long tradeId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new DomainException(TradeErrorCode.TRADE_NOT_FOUND));
        return TradeDto.TradeResponse.from(trade);
    }

    @Transactional
    public List<Long> findReservedStockIds(UUID userId) {
        return tradeRepository.findDistinctStockIdsByUserIdAndTradeType(userId, TradeType.RESERVED);
    }

    @Override
    public List<TradeDto.TradeHistoryResponse> findTradesByMonth(UUID userId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        return tradeRepository.findByUserIdAndCreatedAtBetween(userId, start, end)
                .stream()
                .map(TradeDto.TradeHistoryResponse::from)
                .toList();
    }

    @Override
    public List<TradeDto.TradeHistoryResponse> findTradesByDateRange(UUID userId, LocalDate fromDate, LocalDate toDate) {
        LocalDateTime start = fromDate.atStartOfDay();
        LocalDateTime end = toDate.plusDays(1).atStartOfDay();

        return tradeRepository.findByUserIdAndCreatedAtBetween(userId, start, end)
                .stream()
                .map(TradeDto.TradeHistoryResponse::from)
                .toList();
    }

    @Transactional
    public TradeDto.TradeResponse createTrade(TradeDto.TransactionRequest request, Requester requester) {
        try {
            validateTradeContexts(request, requester);

            TradeOrderType orderType = request.getTradeType();
            if (orderType == null) {
                throw new DomainException(TradeErrorCode.INVALID_TRADE_TYPE);
            }

            if (orderType == TradeOrderType.NORMAL) {
                return processNormalTrade(request, requester.getUuid());
            } else if (orderType == TradeOrderType.RESERVED) {
                return processReservedTrade(request, requester.getUuid());
            }

            throw new DomainException(TradeErrorCode.INVALID_TRADE_TYPE);
        } catch (DomainException ex) {
            recordTradeFailure("create", ex);
            throw ex;
        }
    }

    private void validateTradeContexts(TradeDto.TransactionRequest request, Requester requester) {
//        if(!marketClient.isMarketOpen()) {
//            throw new DomainException(TradeErrorCode.MARKET_CLOSED);
//        }

        if(!assetClient.isExistPortfolio(request.getPortfolioId(), requester.getUuid())) {
            throw new DomainException(TradeErrorCode.PORTFOLIO_NOT_FOUND);
        }

        validateTransactionRequirements(request, requester.getUuid());
    }

    private void validateTransactionRequirements(TradeDto.TransactionRequest request, UUID userId) {
        if (request.getTransactionType() == TransactionType.BUY) {
            ensureSufficientBalanceForBuy(request, userId);
            return;
        }

        if (request.getTransactionType() == TransactionType.SELL) {
            ensureSufficientHoldingAmountForSell(request, userId);
            return;
        }

        throw new DomainException(TradeErrorCode.INVALID_TRADE_TYPE);
    }

    private void ensureSufficientBalanceForBuy(TradeDto.TransactionRequest request, UUID userId) {
        Long balance = walletClient.getWalletBalance(userId);
        BigDecimal required = BigDecimal.valueOf(request.getAmount())
                .multiply(BigDecimal.valueOf(request.getPrice()));

        if (BigDecimal.valueOf(balance).compareTo(required) < 0) {
            throw new DomainException(TradeErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    private void ensureSufficientHoldingAmountForSell(TradeDto.TransactionRequest request, UUID userId) {
        boolean hasSufficientStockAmount = assetClient.hasSufficientStockAmount(
                request.getPortfolioId(),
                userId,
                request.getStockId(),
                request.getAmount()
        );

        if (!hasSufficientStockAmount) {
            throw new DomainException(TradeErrorCode.INSUFFICIENT_HOLDING_AMOUNT);
        }
    }

    @Transactional
    public TradeDto.TradeResponse cancelTrade(Long tradeId, Requester requester) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new DomainException(TradeErrorCode.TRADE_NOT_FOUND));

        ensureTradeCancelable(trade, requester);

        trade.cancel();
        Trade cancelledTrade = tradeRepository.save(trade);
        tradeEventProducer.publishTradeCancelledEvent(cancelledTrade);

        return TradeDto.TradeResponse.from(cancelledTrade);
    }

    // 예약 주문 체결
    @Transactional
    public TradeDto.TradeResponse executeReservedTrade(Long tradeId) {
        try {
            Trade trade = tradeRepository.findById(tradeId)
                    .orElseThrow(() -> new DomainException(TradeErrorCode.TRADE_NOT_FOUND));

            ensureTradeIsReserved(trade);

            if (isInsufficientBalanceForReservedBuy(trade)) {
                recordTradeFailure("execute_reserved", TradeErrorCode.INSUFFICIENT_BALANCE);
                trade.fail();
                Trade failedTrade = tradeRepository.save(trade);
                return TradeDto.TradeResponse.from(failedTrade);
            }

            if (isInsufficientHoldingAmountForReservedSell(trade)) {
                recordTradeFailure("execute_reserved", TradeErrorCode.INSUFFICIENT_HOLDING_AMOUNT);
                trade.fail();
                Trade failedTrade = tradeRepository.save(trade);
                return TradeDto.TradeResponse.from(failedTrade);
            }

            trade.execute();
            Trade saveTrade = tradeRepository.save(trade);

            tradeEventProducer.publishNormalTradeExecutedEvent(trade);
            recordTradeExecuted(TradeType.RESERVED, trade.getTransactionType());
            recordReservedExecutionLatency(trade);

            return TradeDto.TradeResponse.from(saveTrade);
        } catch (DomainException ex) {
            recordTradeFailure("execute_reserved", ex);
            throw ex;
        }
    }

    private void ensureTradeIsReserved(Trade trade) {
        if(trade.getTradeType() != TradeType.RESERVED) {
            throw new DomainException(TradeErrorCode.INVALID_TRADE_TYPE);
        }
    }

    private boolean isInsufficientBalanceForReservedBuy(Trade trade) {
        if (trade.getTransactionType() != TransactionType.BUY) {
            return false;
        }

        Long balance = walletClient.getWalletBalance(trade.getUserId());
        BigDecimal required = BigDecimal.valueOf(trade.getAmount())
                .multiply(BigDecimal.valueOf(trade.getPrice()));

        return BigDecimal.valueOf(balance).compareTo(required) < 0;
    }

    private boolean isInsufficientHoldingAmountForReservedSell(Trade trade) {
        if (trade.getTransactionType() != TransactionType.SELL) {
            return false;
        }

        return !assetClient.hasSufficientStockAmount(
                trade.getPortfolioId(),
                trade.getUserId(),
                trade.getStockId(),
                trade.getAmount()
        );
    }

    private boolean isInsufficientRequirementsForReservedTrade(Trade trade) {
        return isInsufficientBalanceForReservedBuy(trade)
                || isInsufficientHoldingAmountForReservedSell(trade);
    }

    private static void ensureTradeCancelable(Trade trade, Requester requester) {
        if (!trade.getUserId().equals(requester.getUuid()) && requester.getRole() != UserRole.ADMIN) {
            throw new DomainException(TradeErrorCode.CANNOT_CANCEL_BY_OTHER_USER);
        }

        if (trade.getTradeType() == TradeType.CANCELLED) {
            throw new DomainException(TradeErrorCode.ALREADY_CANCELLED_TRADE);
        }

        if (trade.getTradeType() != TradeType.RESERVED) {
            throw new DomainException(TradeErrorCode.RESERVED_TRADE_ONLY_CANCELLABLE);
        }
    }

    private TradeDto.TradeResponse processNormalTrade(TradeDto.TransactionRequest request, UUID userId) {
        validateMarketPrice(request);

        String stockName = marketClient.getStockNameById(request.getStockId());

        Trade trade = createTradeFrom(request, stockName, userId);
        Trade savedTrade = tradeRepository.save(trade);

        tradeEventProducer.publishNormalTradeExecutedEvent(trade);
        publishTradeMetricEvent(trade);
        recordTradeCreated(trade);
        recordTradeExecuted(trade.getTradeType(), trade.getTransactionType());

        return TradeDto.TradeResponse.from(savedTrade);
    }

    private void validateMarketPrice(TradeDto.TransactionRequest request) {
        Long currentPrice = marketClient.getCurrentPrice(request.getStockId());
        if (!request.getPrice().equals(currentPrice)) {
            throw new DomainException(TradeErrorCode.MARKET_PRICE_MISMATCH);
        }
    }

    private static Trade createTradeFrom(TradeDto.TransactionRequest request, String stockName, UUID userId) {
        return Trade.create(
                request.getStockId(),
                request.getAmount(),
                request.getPrice(),
                request.getPortfolioId(),
                userId,
                request.getTransactionType(),
                request.getTradeType().toTradeType(),
                stockName
        );
    }

    private void publishTradeMetricEvent(Trade trade) {
        MetricEventType eventType = trade.getTransactionType() == TransactionType.BUY
                ? MetricEventType.STOCK_BOUGHT
                : MetricEventType.STOCK_SOLD;

        gamificationEventProducer.publishUserMetricUpdatedEvent(UserMetricUpdatedEvent.builder()
                .userId(trade.getUserId().toString())
                .eventType(eventType)
                .delta(1.0)
                .occurredAt(Instant.now())
                .build());
    }

    private TradeDto.TradeResponse processReservedTrade(TradeDto.TransactionRequest request, UUID userId) {
        String stockName = marketClient.getStockNameById(request.getStockId());

        Trade trade = createTradeFrom(request, stockName, userId);
        Trade savedTrade = tradeRepository.save(trade);
        tradeEventProducer.publishTradeReservedEvent(trade);
        recordTradeCreated(trade);

        return TradeDto.TradeResponse.from(savedTrade);
    }

    private void recordTradeCreated(Trade trade) {
        meterRegistry.counter(
                "trade.orders.created",
                "order_type", trade.getTradeType().name().toLowerCase(),
                "transaction_type", trade.getTransactionType().name().toLowerCase()
        ).increment();
    }

    private void recordTradeExecuted(TradeType tradeType, TransactionType transactionType) {
        meterRegistry.counter(
                "trade.orders.executed",
                "order_type", tradeType.name().toLowerCase(),
                "transaction_type", transactionType.name().toLowerCase()
        ).increment();
    }

    private void recordTradeFailure(String stage, DomainException ex) {
        recordTradeFailure(stage, ex.getErrorCode().getCode());
    }

    private void recordTradeFailure(String stage, TradeErrorCode errorCode) {
        recordTradeFailure(stage, errorCode.getCode());
    }

    private void recordTradeFailure(String stage, String reason) {
        meterRegistry.counter(
                "trade.orders.failed",
                "stage", stage,
                "reason", reason.toLowerCase()
        ).increment();
    }

    private void recordReservedExecutionLatency(Trade trade) {
        if (trade.getCreatedAt() == null) {
            return;
        }

        Timer.builder("trade.reserved.execution.latency")
                .description("예약 주문 생성 후 실제 체결까지 걸린 시간")
                .tag("transaction_type", trade.getTransactionType().name().toLowerCase())
                .register(meterRegistry)
                .record(java.time.Duration.between(trade.getCreatedAt().atZone(KST).toInstant(), Instant.now()));
    }


}
