package depth.finvibe.modules.study.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@SuperBuilder
public class Lesson {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Course course;

    private String title;

    @Column(length = 2000)
    private String description;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private LessonContent content;

    public void makeRelationshipWith(LessonContent content) {
        this.content = content;
        content.setLesson(this);
    }

    public static Lesson of(Course course, String title, String description) {
        return Lesson.builder()
                .course(course)
                .title(title)
                .description(description)
                .build();
    }
}
