package depth.finvibe.modules.study.infra.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.study.domain.Lesson;

public interface LessonJpaRepository extends JpaRepository<Lesson, Long> {
    long countByCourseId(Long courseId);
    List<Lesson> findByCourseIdOrderByIdAsc(Long courseId);
}
