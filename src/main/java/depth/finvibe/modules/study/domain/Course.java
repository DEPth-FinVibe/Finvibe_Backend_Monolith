package depth.finvibe.modules.study.domain;

import java.util.UUID;

import jakarta.persistence.*;
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
public class Course extends TimeStampedBaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    private CourseDifficulty difficulty;

    private UUID owner;

    private Boolean isGlobal;

    private Integer totalLessonCount;

    public static Course of(String title, String description, CourseDifficulty difficulty, UUID owner) {
        return Course.builder()
                .title(title)
                .description(description)
                .difficulty(difficulty)
                .owner(owner)
                .isGlobal(false)
                .totalLessonCount(0)
                .build();
    }

    public void updateTotalLessonCount(int totalLessonCount) {
        this.totalLessonCount = totalLessonCount;
    }
}
