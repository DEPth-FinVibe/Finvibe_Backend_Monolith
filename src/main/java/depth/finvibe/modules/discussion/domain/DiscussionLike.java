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
@Table(name = "discussion_like", uniqueConstraints = @UniqueConstraint(name = "uk_discussion_like_discussion_user", columnNames = {
        "discussion_id", "user_id" }))
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DiscussionLike extends TimeStampedBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "discussion_id", nullable = false)
    private Discussion discussion;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    public static DiscussionLike create(Discussion discussion, UUID userId) {
        return DiscussionLike.builder()
                .discussion(discussion)
                .userId(userId)
                .build();
    }
}
