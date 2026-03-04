package depth.finvibe.modules.study.application.port.in;

import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.study.dto.CourseDto;

import java.util.List;

public interface CourseQueryUseCase {
    List<String> getRecommendedKeywords(Requester requester);
    CourseDto.ContentPreviewResponse previewCourseContent(CourseDto.CreateRequest request, Requester requester);
    List<CourseDto.MyCourseResponse> getMyCourses(Requester requester);
}
