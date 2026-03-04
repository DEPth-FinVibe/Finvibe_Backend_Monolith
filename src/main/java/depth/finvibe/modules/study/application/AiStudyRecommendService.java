package depth.finvibe.modules.study.application;

import depth.finvibe.modules.study.application.port.in.AiStudyRecommendCommandUseCase;
import depth.finvibe.modules.study.application.port.in.AiStudyRecommendQueryUseCase;
import depth.finvibe.modules.study.application.port.out.AiRecommendGenerator;
import depth.finvibe.modules.study.application.port.out.AiStudyRecommendRepository;
import depth.finvibe.modules.study.application.port.out.TradeServiceClient;
import depth.finvibe.modules.study.domain.AiStudyRecommend;
import depth.finvibe.modules.study.dto.AiStudyRecommendDto;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.common.gamification.dto.TradeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AiStudyRecommendService implements AiStudyRecommendCommandUseCase, AiStudyRecommendQueryUseCase {
    private static final long TRADE_LOOKBACK_DAYS = 30L;

    private final AiRecommendGenerator aiRecommendGenerator;
    private final TradeServiceClient tradeServiceClient;
    private final AiStudyRecommendRepository aiStudyRecommendRepository;

    @Override
    public void createOrGetTodayAiStudyRecommend(UUID userId) {
        createOrGetTodayAiStudyRecommendInternal(userId);
    }

    @Override
    public AiStudyRecommendDto.GetTodayAiStudyRecommendResponse getTodayAiStudyRecommend(Requester requester) {
        AiStudyRecommend aiStudyRecommend = createOrGetTodayAiStudyRecommendInternal(requester.getUuid());
        return AiStudyRecommendDto.GetTodayAiStudyRecommendResponse.builder()
                .content(aiStudyRecommend.getContent())
                .build();
    }

    private AiStudyRecommend createOrGetTodayAiStudyRecommendInternal(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime endOfToday = startOfToday.plusDays(1).minusNanos(1);

        Optional<AiStudyRecommend> existingRecommend = aiStudyRecommendRepository.findByUserId(userId);

        if (aiStudyRecommendRepository.existsByUserIdAndLastModifiedAtBetween(userId, startOfToday, endOfToday)) {
            return existingRecommend.orElseGet(() -> generateAndSaveRecommend(userId, today, Optional.empty()));
        }

        return generateAndSaveRecommend(userId, today, existingRecommend);
    }

    private AiStudyRecommend generateAndSaveRecommend(
            UUID userId,
            LocalDate today,
            Optional<AiStudyRecommend> existingRecommend
    ) {

        LocalDate fromDate = today.minusDays(TRADE_LOOKBACK_DAYS);
        List<TradeDto.TradeHistoryResponse> tradeHistories = tradeServiceClient.getUserTradeHistories(
                userId.toString(),
                fromDate,
                today
        );

        String recentTrades = buildTradePrompt(tradeHistories);
        String recommendationContent = aiRecommendGenerator.generateStudyRecommendContent(recentTrades);

        AiStudyRecommend aiStudyRecommend = existingRecommend
                .orElseGet(() -> AiStudyRecommend.builder().userId(userId).build());
        aiStudyRecommend.updateContent(recommendationContent);
        return aiStudyRecommendRepository.save(aiStudyRecommend);
    }

    private String buildTradePrompt(List<TradeDto.TradeHistoryResponse> tradeHistories) {
        if (tradeHistories == null || tradeHistories.isEmpty()) {
            return "최근 30일 거래 내역이 없습니다.";
        }

        return tradeHistories.stream()
                .sorted((left, right) -> {
                    LocalDateTime leftTime = left.getCreatedAt();
                    LocalDateTime rightTime = right.getCreatedAt();
                    if (leftTime == null && rightTime == null) {
                        return 0;
                    }
                    if (leftTime == null) {
                        return 1;
                    }
                    if (rightTime == null) {
                        return -1;
                    }
                    return rightTime.compareTo(leftTime);
                })
                .map(trade -> String.format(
                        "%s | %s | %s | 수량: %s | 가격: %s",
                        String.valueOf(trade.getCreatedAt()),
                        String.valueOf(trade.getStockName()),
                        String.valueOf(trade.getTransactionType()),
                        String.valueOf(trade.getAmount()),
                        String.valueOf(trade.getPrice())
                ))
                .collect(Collectors.joining("\n"));
    }
}
