package depth.finvibe.modules.study.domain;

import depth.finvibe.common.gamification.domain.TimeStampedBaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@SuperBuilder
public class LessonComplete extends TimeStampedBaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Lesson lesson;

    private UUID userId;

    @Column(unique = true)
    @Getter(value = AccessLevel.PROTECTED)
    private String lessonUserIdKey;

    public static LessonComplete of(Lesson lesson, UUID userId) {
        return LessonComplete.builder()
                .lesson(lesson)
                .userId(userId)
                .lessonUserIdKey(generateLessonUserIdKey(lesson.getId(), userId))
                .build();
    }

    public static String generateLessonUserIdKey(Long lessonId, UUID userId) {
        return lessonId + "_" + userId.toString();
    }
}
