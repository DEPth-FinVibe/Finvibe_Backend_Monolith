package depth.finvibe.modules.asset.api.external;

import jakarta.validation.Valid;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.asset.application.port.in.AssetCommandUseCase;
import depth.finvibe.modules.asset.application.port.in.AssetQueryUseCase;
import depth.finvibe.modules.asset.dto.PortfolioGroupDto;

@RestController
@RequestMapping("/portfolios")
@RequiredArgsConstructor
@Tag(name = "포트폴리오", description = "포트폴리오 그룹 API")
public class PortfolioController {
    private final AssetCommandUseCase commandUseCase;
    private final AssetQueryUseCase queryUseCase;

    @GetMapping
    @Operation(summary = "포트폴리오 그룹 조회", description = "사용자의 포트폴리오 그룹 목록을 조회합니다.")
    public ResponseEntity<List<PortfolioGroupDto.PortfolioGroupResponse>> getPortfoliosByUser(
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        return ResponseEntity.ok(queryUseCase.getPortfoliosByUser(requester.getUuid()));
    }

    @PostMapping
    @Operation(summary = "포트폴리오 그룹 생성", description = "포트폴리오 그룹을 생성합니다.")
    public ResponseEntity<Void> createPortfolioGroup(
            @RequestBody @Valid PortfolioGroupDto.CreatePortfolioGroupRequest request,
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        commandUseCase.createPortfolioGroup(request, requester.getUuid());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PatchMapping("/{portfolioGroupId}")
    @Operation(summary = "포트폴리오 그룹 수정", description = "포트폴리오 그룹을 수정합니다.")
    public ResponseEntity<Void> updatePortfolioGroup(
            @Parameter(description = "포트폴리오 그룹 ID", example = "1") @PathVariable Long portfolioGroupId,
            @RequestBody @Valid PortfolioGroupDto.UpdatePortfolioGroupRequest request,
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        commandUseCase.updatePortfolioGroup(portfolioGroupId, request, requester.getUuid());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{portfolioGroupId}")
    @Operation(summary = "포트폴리오 그룹 삭제", description = "포트폴리오 그룹을 삭제합니다.")
    public ResponseEntity<Void> deletePortfolioGroup(
            @Parameter(description = "포트폴리오 그룹 ID", example = "1") @PathVariable Long portfolioGroupId,
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        commandUseCase.deletePortfolioGroup(portfolioGroupId, requester.getUuid());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{sourcePortfolioId}/assets/{assetId}/transfer")
    @Operation(summary = "자산 이동", description = "특정 자산을 다른 포트폴리오 그룹으로 이동합니다.")
    public ResponseEntity<Void> transferAsset(
            @Parameter(description = "원본 포트폴리오 그룹 ID", example = "1") @PathVariable Long sourcePortfolioId,
            @Parameter(description = "자산 ID", example = "101") @PathVariable Long assetId,
            @RequestBody @Valid PortfolioGroupDto.TransferAssetRequest request,
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        commandUseCase.transferAsset(sourcePortfolioId, assetId, request, requester.getUuid());
        return ResponseEntity.noContent().build();
    }
}
