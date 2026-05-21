package depth.finvibe.modules.study.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import depth.finvibe.modules.study.domain.LessonComplete;

public interface LessonCompleteRepository {
    LessonComplete save(LessonComplete lessonComplete);
    boolean existsByLessonUserIdKey(String lessonUserIdKey);
    long countByLessonCourseIdAndUserId(Long courseId, Long userId);
    List<Long> findLessonIdsByUserIdAndCourseId(Long userId, Long courseId);
    List<LessonComplete> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);
}
