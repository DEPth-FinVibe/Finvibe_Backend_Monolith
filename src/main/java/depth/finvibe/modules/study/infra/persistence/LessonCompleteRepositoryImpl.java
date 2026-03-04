package depth.finvibe.modules.study.infra.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.study.application.port.out.LessonCompleteRepository;
import depth.finvibe.modules.study.domain.LessonComplete;

@Repository
@RequiredArgsConstructor
public class LessonCompleteRepositoryImpl implements LessonCompleteRepository {
    private final LessonCompleteJpaRepository lessonCompleteJpaRepository;

    @Override
    public LessonComplete save(LessonComplete lessonComplete) {
        return lessonCompleteJpaRepository.save(lessonComplete);
    }

    @Override
    public boolean existsByLessonUserIdKey(String lessonUserIdKey) {
        return lessonCompleteJpaRepository.existsByLessonUserIdKey(lessonUserIdKey);
    }

    @Override
    public long countByLessonCourseIdAndUserId(Long courseId, UUID userId) {
        return lessonCompleteJpaRepository.countByLessonCourseIdAndUserId(courseId, userId);
    }

    @Override
    public List<Long> findLessonIdsByUserIdAndCourseId(UUID userId, Long courseId) {
        return lessonCompleteJpaRepository.findByUserIdAndLessonCourseId(userId, courseId).stream()
                .map(lessonComplete -> lessonComplete.getLesson().getId())
                .toList();
    }

    @Override
    public List<LessonComplete> findByUserIdAndCreatedAtBetween(UUID userId, LocalDateTime start, LocalDateTime end) {
        return lessonCompleteJpaRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(userId, start, end);
    }
}
