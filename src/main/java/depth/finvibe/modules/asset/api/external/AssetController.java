package depth.finvibe.modules.asset.api.external;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.asset.application.port.in.AssetQueryUseCase;
import depth.finvibe.modules.asset.dto.PortfolioGroupDto;

@RestController
@RequiredArgsConstructor
@Tag(name = "자산", description = "자산 API")
public class AssetController {

    private final AssetQueryUseCase queryUseCase;

    @GetMapping("/portfolios/{portfolioId}/assets")
    @Operation(summary = "포트폴리오 자산 조회", description = "포트폴리오에 속한 자산 목록을 조회합니다.")
    public ResponseEntity<List<PortfolioGroupDto.AssetResponse>> getAssetsByPortfolio(
            @Parameter(description = "포트폴리오 ID", example = "1") @PathVariable Long portfolioId,
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        return ResponseEntity.ok(queryUseCase.getAssetsByPortfolio(portfolioId, requester.getUuid()));
    }

    @GetMapping("/assets/allocation")
    @Operation(summary = "전체 자산 배분 조회", description = "사용자의 전체 자산 배분(현금/주식)과 기준금액 대비 증감 정보를 조회합니다.")
    public ResponseEntity<PortfolioGroupDto.AssetAllocationResponse> getAssetAllocation(
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        return ResponseEntity.ok(queryUseCase.getAssetAllocation(requester.getUuid()));
    }
}
