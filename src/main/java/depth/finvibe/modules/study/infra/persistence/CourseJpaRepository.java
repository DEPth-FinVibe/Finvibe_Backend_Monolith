package depth.finvibe.modules.study.infra.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.study.domain.Course;

public interface CourseJpaRepository extends JpaRepository<Course, Long> {
    boolean existsByIsGlobalTrue();
    List<Course> findByOwnerOrIsGlobalTrue(UUID owner);
}
