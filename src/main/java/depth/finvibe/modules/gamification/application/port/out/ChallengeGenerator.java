package depth.finvibe.modules.gamification.application.port.out;

import depth.finvibe.modules.gamification.dto.ChallengeDto;

import java.util.List;

public interface ChallengeGenerator {

    List<ChallengeDto.ChallengeGenerationResponse> generate();

}
