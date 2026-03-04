package depth.finvibe.modules.study.infra.scheduler;

import java.nio.charset.StandardCharsets;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import depth.finvibe.modules.study.application.port.out.CourseRepository;
import depth.finvibe.modules.study.application.port.out.LessonRepository;
import depth.finvibe.modules.study.domain.Course;
import depth.finvibe.modules.study.domain.CourseDifficulty;
import depth.finvibe.modules.study.domain.Lesson;
import depth.finvibe.modules.study.domain.LessonContent;
import depth.finvibe.common.gamification.lock.DistributedLockManager;
import depth.finvibe.common.gamification.lock.LockAcquisitionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class GlobalCourseStartupInitializer implements ApplicationRunner {

    private static final String LOCK_KEY = "study_global_course_init";
    private static final String SEED_PATH = "classpath:seed/global-courses.json";

    private final DistributedLockManager distributedLockManager;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final ObjectMapper objectMapper;
    @Qualifier("webApplicationContext")
    private final ResourceLoader resourceLoader;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            distributedLockManager.executeWithLock(LOCK_KEY, () -> {
                if (courseRepository.existsByIsGlobalTrue()) {
                    log.info("Global courses already exist. skip initialization");
                    return null;
                }

                SeedCourses seedCourses = loadSeedCourses();
                if (seedCourses == null || seedCourses.courses == null || seedCourses.courses.isEmpty()) {
                    log.warn("Global course seed data is empty. skip initialization");
                    return null;
                }

                seedCourses.courses.forEach(this::saveGlobalCourse);
                log.info("Global course initialization completed. total={}", seedCourses.courses.size());
                return null;
            });
        } catch (LockAcquisitionException ex) {
            log.warn("Skip global course initialization due to lock acquisition failure", ex);
        }
    }

    private void saveGlobalCourse(SeedCourse seedCourse) {
        int totalLessonCount = seedCourse.lessons == null ? 0 : seedCourse.lessons.size();
        Course course = Course.builder()
                .title(seedCourse.title)
                .description(seedCourse.description)
                .difficulty(seedCourse.difficulty)
                .owner(null)
                .isGlobal(true)
                .totalLessonCount(totalLessonCount)
                .build();

        Course savedCourse = courseRepository.save(course);
        List<Lesson> lessons = createLessons(savedCourse, seedCourse.lessons);
        lessonRepository.saveAll(lessons);
    }

    private List<Lesson> createLessons(Course course, List<SeedLesson> seedLessons) {
        if (seedLessons == null || seedLessons.isEmpty()) {
            return List.of();
        }
        return seedLessons.stream()
                .map(seedLesson -> {
                    Lesson lesson = Lesson.of(course, seedLesson.title, seedLesson.description);
                    String content = seedLesson.content == null ? "" : seedLesson.content;
                    LessonContent lessonContent = LessonContent.of(content);
                    lesson.makeRelationshipWith(lessonContent);
                    return lesson;
                })
                .toList();
    }

    private SeedCourses loadSeedCourses() {
        Resource resource = resourceLoader.getResource(SEED_PATH);
        try {
            byte[] bytes = resource.getInputStream().readAllBytes();
            String json = new String(bytes, StandardCharsets.UTF_8).trim();
            return objectMapper.readValue(json, SeedCourses.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load global course seed data", ex);
        }
    }

    private static class SeedCourses {
        public List<SeedCourse> courses;
    }

    private static class SeedCourse {
        public String title;
        public String description;
        public CourseDifficulty difficulty;
        public List<SeedLesson> lessons;
    }

    private static class SeedLesson {
        public String title;
        public String description;
        public String content;
    }
}
