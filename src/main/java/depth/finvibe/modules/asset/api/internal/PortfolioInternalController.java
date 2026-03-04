package depth.finvibe.modules.asset.api.internal;

import depth.finvibe.modules.asset.application.port.in.AssetQueryUseCase;
import depth.finvibe.modules.asset.dto.PortfolioGroupDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/portfolios")
@RequiredArgsConstructor
public class PortfolioInternalController {
    private final AssetQueryUseCase queryUseCase;

    @GetMapping
    public ResponseEntity<List<PortfolioGroupDto.PortfolioGroupResponse>> getPortfoliosByUser(
            @RequestParam UUID memberId
    ) {
        return ResponseEntity.ok(queryUseCase.getPortfoliosByUser(memberId));
    }
}
