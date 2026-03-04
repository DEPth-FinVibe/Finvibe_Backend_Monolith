package depth.finvibe.modules.study.api.external;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.study.application.port.in.AiStudyRecommendQueryUseCase;
import depth.finvibe.modules.study.dto.AiStudyRecommendDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 학습 추천", description = "오늘의 AI 학습 추천 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/study/ai-recommends")
public class StudyAiRecommendController {
    private final AiStudyRecommendQueryUseCase aiStudyRecommendQueryUseCase;

    @Operation(summary = "오늘의 AI 학습 추천 조회", description = "오늘 기준 AI 학습 추천을 조회하며 없으면 생성합니다")
    @GetMapping("/today")
    public AiStudyRecommendDto.GetTodayAiStudyRecommendResponse getTodayAiStudyRecommend(
            @AuthenticatedUser Requester requester
    ) {
        return aiStudyRecommendQueryUseCase.getTodayAiStudyRecommend(requester);
    }
}
