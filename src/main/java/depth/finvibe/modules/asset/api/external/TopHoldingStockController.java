package depth.finvibe.modules.asset.api.external;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.asset.application.port.in.AssetQueryUseCase;
import depth.finvibe.modules.asset.dto.TopHoldingStockDto;

@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
@Tag(name = "자산", description = "자산 API")
public class TopHoldingStockController {
    private final AssetQueryUseCase queryUseCase;

    @GetMapping("/top-100")
    @Operation(summary = "전체 보유 종목 TOP100 조회", description = "전체 사용자 보유 수량 합계 기준 TOP 종목을 조회합니다.")
    public ResponseEntity<TopHoldingStockDto.TopHoldingStockListResponse> getTopHoldingStocks(
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        return ResponseEntity.ok(queryUseCase.getTopHoldingStocks(requester.getUuid()));
    }
}
