package depth.finvibe.modules.trade.api.external;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.trade.application.port.in.TradeCommandUseCase;
import depth.finvibe.modules.trade.application.port.in.TradeQueryUseCase;
import depth.finvibe.modules.trade.dto.TradeDto;

@RestController
@RequestMapping("/trades")
@RequiredArgsConstructor
@Tag(name = "거래", description = "거래 API")
public class TradeController {
    private final TradeCommandUseCase tradeCommandUseCase;
    private final TradeQueryUseCase tradeQueryUseCase;

    @GetMapping("/{tradeId}")
    @Operation(summary = "거래 조회", description = "거래 ID로 거래 상태를 조회합니다.")
    public ResponseEntity<TradeDto.TradeResponse> getTradeStatus(
            @Parameter(description = "거래 ID", example = "123") @PathVariable Long tradeId
    ) {
        TradeDto.TradeResponse response = tradeQueryUseCase.findTrade(tradeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reserved/stock-ids")
    @Operation(summary = "예약 종목 ID 조회", description = "사용자의 예약 종목 ID 목록을 조회합니다.")
    public ResponseEntity<List<Long>> getReservedStockIds(
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        List<Long> stockIds = tradeQueryUseCase.findReservedStockIds(requester.getUuid());
        return ResponseEntity.ok(stockIds);
    }

    @GetMapping("/history")
    @Operation(summary = "월별 거래 기록 조회", description = "해당 월의 거래 기록을 최신순으로 조회합니다.")
    public ResponseEntity<List<TradeDto.TradeHistoryResponse>> getTradeHistory(
            @Parameter(description = "연도", example = "2026") @RequestParam int year,
            @Parameter(description = "월", example = "2") @RequestParam int month,
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        List<TradeDto.TradeHistoryResponse> response =
                tradeQueryUseCase.findTradesByMonth(requester.getUuid(), year, month);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}/history")
    @Operation(summary = "특정 사용자 월별 거래 기록 조회", description = "해당 사용자의 특정 월 거래 기록을 최신순으로 조회합니다.")
    public ResponseEntity<List<TradeDto.TradeHistoryResponse>> getTradeHistoryByUserId(
            @Parameter(description = "사용자 ID", example = "00000000-0000-0000-0000-000000000000") @PathVariable UUID userId,
            @Parameter(description = "연도", example = "2026") @RequestParam int year,
            @Parameter(description = "월", example = "2") @RequestParam int month,
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        List<TradeDto.TradeHistoryResponse> response = tradeQueryUseCase.findTradesByMonth(userId, year, month);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "거래 생성", description = "신규 거래 주문을 생성합니다.")
    public ResponseEntity<TradeDto.TradeResponse> placeTrade(
            @RequestBody @Valid TradeDto.TransactionRequest request,
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        TradeDto.TradeResponse response = tradeCommandUseCase.createTrade(request, requester);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{tradeId}")
    @Operation(summary = "거래 취소", description = "거래 주문을 취소합니다.")
    public ResponseEntity<TradeDto.TradeResponse> cancelTrade(
            @Parameter(description = "거래 ID", example = "123") @PathVariable Long tradeId,
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        TradeDto.TradeResponse response = tradeCommandUseCase.cancelTrade(tradeId, requester);
        return ResponseEntity.ok(response);
    }
}
