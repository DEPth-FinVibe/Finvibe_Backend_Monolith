package depth.finvibe.modules.trade.api.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import depth.finvibe.modules.trade.application.port.in.TradeQueryUseCase;
import depth.finvibe.modules.trade.dto.TradeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/trades")
@RequiredArgsConstructor
public class TradeInternalController {
    private final TradeQueryUseCase tradeQueryUseCase;

    @GetMapping("/{tradeId}")
    public ResponseEntity<TradeDto.TradeResponse> getTradeStatus(
            @PathVariable Long tradeId
    ) {
        TradeDto.TradeResponse response = tradeQueryUseCase.findTrade(tradeId);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/history")
    public ResponseEntity<List<TradeDto.TradeHistoryResponse>> getUserTradeHistories(
            @RequestParam String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        List<TradeDto.TradeHistoryResponse> response =
                tradeQueryUseCase.findTradesByDateRange(UUID.fromString(userId), fromDate, toDate);
        return ResponseEntity.ok(response);
    }

}
