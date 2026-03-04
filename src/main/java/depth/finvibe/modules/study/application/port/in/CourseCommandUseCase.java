package depth.finvibe.modules.study.application.port.in;

import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.study.dto.CourseDto;

public interface CourseCommandUseCase {
    void createCourse(CourseDto.CreateRequest request, Requester requester);
    void completeLesson(Long lessonId, Requester requester);
}
