package depth.finvibe.modules.study.api.external;

import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.study.application.port.in.CourseCommandUseCase;
import depth.finvibe.modules.study.application.port.in.LessonQueryUseCase;
import depth.finvibe.modules.study.dto.LessonCompletionDto;
import depth.finvibe.modules.study.dto.LessonDto;

@Tag(name = "학습 레슨", description = "레슨 조회 및 완료 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/study/lessons")
public class StudyLessonController {

    private final LessonQueryUseCase lessonQueryUseCase;
    private final CourseCommandUseCase courseCommandUseCase;

    @Operation(summary = "레슨 상세 조회", description = "레슨 상세와 완료 여부를 조회합니다")
    @GetMapping("/{lessonId}")
    public LessonDto.LessonDetailResponse getLessonDetail(
            @PathVariable Long lessonId,
            @AuthenticatedUser Requester requester
    ) {
        return lessonQueryUseCase.getLessonDetail(lessonId, requester);
    }

    @Operation(summary = "레슨 완료 처리", description = "레슨 완료를 기록하고 경험치 이벤트를 발행합니다")
    @PostMapping("/{lessonId}/complete")
    public void completeLesson(
            @PathVariable Long lessonId,
            @AuthenticatedUser Requester requester
    ) {
        courseCommandUseCase.completeLesson(lessonId, requester);
    }

    @Operation(summary = "월별 레슨 수료 이력 조회", description = "인증된 사용자의 월별 레슨 수료 목록을 조회합니다")
    @GetMapping("/completions/me")
    public LessonCompletionDto.MonthlyLessonCompletionResponse getMonthlyLessonCompletions(
            @RequestParam String month,
            @AuthenticatedUser Requester requester
    ) {
        return lessonQueryUseCase.getMonthlyLessonCompletions(requester, month);
    }
}
