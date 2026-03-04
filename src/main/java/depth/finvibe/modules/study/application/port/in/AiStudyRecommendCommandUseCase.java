package depth.finvibe.modules.study.application.port.in;

import java.util.UUID;

public interface AiStudyRecommendCommandUseCase {
    void createOrGetTodayAiStudyRecommend(UUID userId);
}
