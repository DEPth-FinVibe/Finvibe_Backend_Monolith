package depth.finvibe.modules.study.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import depth.finvibe.common.gamification.domain.TimeStampedBaseEntity;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@SuperBuilder
public class CourseProgress extends TimeStampedBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Course course;

    private UUID userId;

    private Integer completedLessonCount;

    private Integer totalLessonCount;

    @Column(unique = true)
    @Getter(value = AccessLevel.PROTECTED)
    private String courseUserIdKey;

    public static CourseProgress of(Course course, UUID userId, int totalLessonCount, int completedLessonCount) {
        return CourseProgress.builder()
                .course(course)
                .userId(userId)
                .totalLessonCount(totalLessonCount)
                .completedLessonCount(completedLessonCount)
                .courseUserIdKey(generateCourseUserIdKey(course.getId(), userId))
                .build();
    }

    public static String generateCourseUserIdKey(Long courseId, UUID userId) {
        return courseId + "_" + userId.toString();
    }

    public void updateTotalLessonCount(int totalLessonCount) {
        this.totalLessonCount = totalLessonCount;
    }

    public void updateCompletedLessonCount(int completedLessonCount) {
        this.completedLessonCount = completedLessonCount;
    }
}
