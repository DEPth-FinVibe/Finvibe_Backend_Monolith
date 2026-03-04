package depth.finvibe.modules.study.application.port.in;

import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.study.dto.AiStudyRecommendDto;

public interface AiStudyRecommendQueryUseCase {
    AiStudyRecommendDto.GetTodayAiStudyRecommendResponse getTodayAiStudyRecommend(Requester requester);
}
