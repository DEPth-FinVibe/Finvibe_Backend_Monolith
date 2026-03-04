package depth.finvibe.modules.study.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.study.application.port.out.CourseRepository;
import depth.finvibe.modules.study.domain.Course;

@Repository
@RequiredArgsConstructor
public class CourseRepositoryImpl implements CourseRepository {
    private final CourseJpaRepository courseJpaRepository;

    @Override
    public Course save(Course course) {
        return courseJpaRepository.save(course);
    }

    @Override
    public boolean existsByIsGlobalTrue() {
        return courseJpaRepository.existsByIsGlobalTrue();
    }

    @Override
    public List<Course> findByOwnerOrIsGlobalTrue(UUID owner) {
        return courseJpaRepository.findByOwnerOrIsGlobalTrue(owner);
    }

    @Override
    public Optional<Course> findById(Long id) {
        return courseJpaRepository.findById(id);
    }
}
