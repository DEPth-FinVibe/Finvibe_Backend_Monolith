package depth.finvibe.modules.discussion.domain;

import depth.finvibe.common.insight.domain.TimeStampedBaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "discussion")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Discussion extends TimeStampedBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "news_id")
    private Long newsId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 2000)
    private String content;

    @Builder.Default
    @Column(name = "is_edited", nullable = false)
    private boolean isEdited = false;

    @Builder.Default
    @OneToMany(mappedBy = "discussion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DiscussionComment> comments = new ArrayList<>();

    public static Discussion create(Long newsId, UUID userId, String content) {
        return Discussion.builder()
                .newsId(newsId)
                .userId(userId)
                .content(content)
                .isEdited(false)
                .build();
    }

    public void updateContent(String content) {
        this.content = content;
        this.isEdited = true;
    }
}
