package depth.finvibe.modules.discussion.dto;

import depth.finvibe.modules.discussion.domain.DiscussionComment;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DiscussionCommentDto {

    @Getter
    @NoArgsConstructor
    public static class CreateRequest {
        private String content;
    }

    @Getter
    public static class Response {
        private final Long id;
        private final UUID userId;
        private final String content;
        private final boolean isEdited;
        private final long likeCount;
        private final LocalDateTime createdAt;

        public Response(DiscussionComment comment, long likeCount) {
            this.id = comment.getId();
            this.userId = comment.getUserId();
            this.content = comment.getContent();
            this.isEdited = comment.isEdited();
            this.likeCount = likeCount; // 좋아요 수 추가
            this.createdAt = comment.getCreatedAt();
        }
    }
}
