package depth.finvibe.modules.study.infra.persistence;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.study.application.port.out.LessonRepository;
import depth.finvibe.modules.study.domain.Lesson;

@Repository
@RequiredArgsConstructor
public class LessonRepositoryImpl implements LessonRepository {
    private final LessonJpaRepository lessonJpaRepository;

    @Override
    public Lesson save(Lesson lesson) {
        return lessonJpaRepository.save(lesson);
    }

    @Override
    public List<Lesson> saveAll(List<Lesson> lessons) {
        return lessonJpaRepository.saveAll(lessons);
    }

    @Override
    public Optional<Lesson> findById(Long id) {
        return lessonJpaRepository.findById(id);
    }

    @Override
    public long countByCourseId(Long courseId) {
        return lessonJpaRepository.countByCourseId(courseId);
    }

    @Override
    public List<Lesson> findByCourseIdOrderByIdAsc(Long courseId) {
        return lessonJpaRepository.findByCourseIdOrderByIdAsc(courseId);
    }
}
