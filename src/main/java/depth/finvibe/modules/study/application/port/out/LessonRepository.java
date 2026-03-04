package depth.finvibe.modules.study.application.port.out;

import depth.finvibe.modules.study.domain.Lesson;

import java.util.List;
import java.util.Optional;

public interface LessonRepository {
    Lesson save(Lesson lesson);
    List<Lesson> saveAll(List<Lesson> lessons);
    Optional<Lesson> findById(Long id);
    long countByCourseId(Long courseId);
    List<Lesson> findByCourseIdOrderByIdAsc(Long courseId);
}
