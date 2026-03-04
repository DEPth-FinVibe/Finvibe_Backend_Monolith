package depth.finvibe.modules.study.application.port.in;

import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.study.dto.LessonCompletionDto;
import depth.finvibe.modules.study.dto.LessonDto;

public interface LessonQueryUseCase {
    LessonDto.LessonDetailResponse getLessonDetail(Long lessonId, Requester requester);
    LessonCompletionDto.MonthlyLessonCompletionResponse getMonthlyLessonCompletions(Requester requester, String month);
}
