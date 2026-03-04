package depth.finvibe.modules.gamification.api.external;

import java.util.List;

import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.gamification.application.port.in.SquadCommandUseCase;
import depth.finvibe.modules.gamification.application.port.in.SquadQueryUseCase;
import depth.finvibe.modules.gamification.dto.SquadDto;

@Tag(name = "스쿼드", description = "스쿼드 조회 및 참여 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/squads")
public class SquadController {

    private final SquadCommandUseCase squadCommandUseCase;
    private final SquadQueryUseCase squadQueryUseCase;

    @Operation(summary = "전체 스쿼드 목록 조회", description = "등록된 모든 스쿼드를 조회합니다")
    @GetMapping
    public List<SquadDto.Response> getAllSquads() {
        return squadQueryUseCase.getAllSquads();
    }

    @Operation(summary = "내 스쿼드 조회", description = "인증된 사용자가 소속된 스쿼드를 반환합니다")
    @GetMapping("/me")
    public SquadDto.Response getMySquad(@AuthenticatedUser Requester requester) {
        return squadQueryUseCase.getUserSquad(requester.getUuid());
    }

    @Operation(summary = "스쿼드 참여", description = "스쿼드에 참여합니다")
    @PostMapping("/{squadId}/join")
    public void joinSquad(
            @PathVariable Long squadId,
            @AuthenticatedUser Requester requester) {
        squadCommandUseCase.joinSquad(squadId, requester);
    }
}
