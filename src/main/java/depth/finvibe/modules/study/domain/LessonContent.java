package depth.finvibe.modules.study.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@SuperBuilder
public class LessonContent {
    @Id @GeneratedValue
    private Long id;

    @OneToOne(mappedBy = "content")
    @Setter
    private Lesson lesson;

    @Lob
    private String content;

    public static LessonContent of(String content) {
        return LessonContent.builder()
                .content(content)
                .build();
    }
}
