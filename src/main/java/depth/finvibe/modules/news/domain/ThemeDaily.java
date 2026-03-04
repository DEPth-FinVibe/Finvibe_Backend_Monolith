package depth.finvibe.modules.news.domain;

import depth.finvibe.common.insight.domain.TimeStampedBaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@Getter
@Table(name = "theme_daily")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SuperBuilder
public class ThemeDaily extends TimeStampedBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate themeDate;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "category_name")
    private String categoryName;

    private String analysis;

    public static ThemeDaily create(LocalDate themeDate, Long categoryId, String categoryName, String analysis) {
        return ThemeDaily.builder()
                .themeDate(themeDate)
                .categoryId(categoryId)
                .categoryName(categoryName)
                .analysis(analysis)
                .build();
    }
}
