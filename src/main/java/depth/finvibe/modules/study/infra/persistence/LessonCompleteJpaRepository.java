package depth.finvibe.modules.study.infra.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.study.domain.LessonComplete;

public interface LessonCompleteJpaRepository extends JpaRepository<LessonComplete, Long> {
    boolean existsByLessonUserIdKey(String lessonUserIdKey);
    long countByLessonCourseIdAndUserId(Long courseId, Long userId);
    List<LessonComplete> findByUserIdAndLessonCourseId(Long userId, Long courseId);
    List<LessonComplete> findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );
}
