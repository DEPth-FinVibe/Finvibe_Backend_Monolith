package depth.finvibe.modules.study.application.port.out;

import java.util.Optional;
import java.util.UUID;

import depth.finvibe.modules.study.domain.CourseProgress;

public interface CourseProgressRepository {
    CourseProgress save(CourseProgress courseProgress);
    Optional<CourseProgress> findByCourseUserIdKey(String courseUserIdKey);
    Optional<CourseProgress> findByCourseIdAndUserId(Long courseId, UUID userId);
}
