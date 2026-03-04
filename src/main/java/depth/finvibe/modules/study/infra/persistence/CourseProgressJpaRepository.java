package depth.finvibe.modules.study.infra.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.study.domain.CourseProgress;

public interface CourseProgressJpaRepository extends JpaRepository<CourseProgress, Long> {
    Optional<CourseProgress> findByCourseUserIdKey(String courseUserIdKey);
    Optional<CourseProgress> findByCourseIdAndUserId(Long courseId, UUID userId);
}
