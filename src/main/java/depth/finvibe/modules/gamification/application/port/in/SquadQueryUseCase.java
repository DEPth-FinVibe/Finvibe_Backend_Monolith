package depth.finvibe.modules.gamification.application.port.in;

import depth.finvibe.modules.gamification.dto.SquadDto;

import java.util.List;
import java.util.UUID;

public interface SquadQueryUseCase {
    /**
     * 사용자의 소속 스쿼드 정보를 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 스쿼드 정보 DTO
     */
    SquadDto.Response getUserSquad(UUID userId);

    /**
     * 전체 스쿼드 목록을 조회합니다.
     *
     * @return 스쿼드 목록 DTO
     */
    List<SquadDto.Response> getAllSquads();
}
