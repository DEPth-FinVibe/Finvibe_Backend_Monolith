package depth.finvibe.modules.discussion.domain;

import depth.finvibe.common.insight.domain.TimeStampedBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "discussion_comment_like", uniqueConstraints = @UniqueConstraint(
        name = "uk_discussion_comment_like_comment_user", columnNames = { "comment_id", "user_id" }))
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DiscussionCommentLike extends TimeStampedBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private DiscussionComment comment;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    public static DiscussionCommentLike create(DiscussionComment comment, UUID userId) {
        return DiscussionCommentLike.builder()
                .comment(comment)
                .userId(userId)
                .build();
    }
}
