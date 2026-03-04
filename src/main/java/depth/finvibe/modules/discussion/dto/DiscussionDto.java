package depth.finvibe.modules.discussion.dto;

import depth.finvibe.modules.discussion.domain.Discussion;
import depth.finvibe.modules.discussion.domain.DiscussionComment;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DiscussionDto {

    @Getter
    @NoArgsConstructor
    public static class QueryRequest {
        private Long newsId;
        private DiscussionSortType sort = DiscussionSortType.LATEST;
    }

    @Getter
    @NoArgsConstructor
    public static class CreateRequest {
        private Long newsId;
        private String content;
    }

    @Getter
    @NoArgsConstructor
    public static class CountQueryRequest {
        private List<Long> newsIds;
    }

    @Getter
    public static class Response {
        private final Long id;
        private final UUID userId;
        private final String content;
        private final Long newsId;
        private final long likeCount;
        private final boolean isEdited;
        private final List<CommentResponse> comments;
        private final LocalDateTime createdAt;

        public Response(Discussion discussion, long likeCount, List<CommentResponse> comments) {
            this.id = discussion.getId();
            this.userId = discussion.getUserId();
            this.content = discussion.getContent();
            this.newsId = discussion.getNewsId();
            this.likeCount = likeCount;
            this.isEdited = discussion.isEdited();
            this.comments = comments;
            this.createdAt = discussion.getCreatedAt();
        }
    }

    @Getter
    public static class CommentResponse {
        private final Long id;
        private final UUID userId;
        private final String content;
        private final boolean isEdited;
        private final long likeCount;
        private final LocalDateTime createdAt;

        public CommentResponse(DiscussionComment comment, long likeCount) {
            this.id = comment.getId();
            this.userId = comment.getUserId();
            this.content = comment.getContent();
            this.isEdited = comment.isEdited();
            this.likeCount = likeCount;
            this.createdAt = comment.getCreatedAt();
        }
    }
}
