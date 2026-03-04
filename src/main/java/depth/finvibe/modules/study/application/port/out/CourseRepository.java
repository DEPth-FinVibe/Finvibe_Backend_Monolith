package depth.finvibe.modules.study.application.port.out;

import depth.finvibe.modules.study.domain.Course;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository {
    Course save(Course course);

    boolean existsByIsGlobalTrue();

    List<Course> findByOwnerOrIsGlobalTrue(UUID owner);

    Optional<Course> findById(Long id);
}
