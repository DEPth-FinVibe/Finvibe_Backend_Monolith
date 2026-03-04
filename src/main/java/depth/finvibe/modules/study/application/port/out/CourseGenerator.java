package depth.finvibe.modules.study.application.port.out;

import depth.finvibe.modules.study.dto.GeneratorDto;

import java.util.List;

public interface CourseGenerator {
    String generateCoursePreview(String title, List<String> keywords);

    String generateCourseDescription(String title, List<String> keywords, List<GeneratorDto.LessonIndex> lessonIndices);
}
