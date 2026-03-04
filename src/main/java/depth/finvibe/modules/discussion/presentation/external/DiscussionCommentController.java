package depth.finvibe.modules.discussion.presentation.external;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.discussion.application.port.in.CommentCommandUseCase;
import depth.finvibe.modules.discussion.application.port.in.DiscussionQueryUseCase;
import depth.finvibe.modules.discussion.dto.DiscussionCommentDto;
import depth.finvibe.modules.discussion.dto.DiscussionDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/discussions")
@RequiredArgsConstructor
@Tag(name = "토론 댓글", description = "토론 댓글 작업")
public class DiscussionCommentController {

    private final DiscussionQueryUseCase discussionQueryUseCase;
    private final CommentCommandUseCase commentCommandUseCase;

    /**
     * 특정 토론의 댓글 목록을 조회합니다.
     */
    @GetMapping("/{discussionId}/comments")
    @Operation(
            summary = "댓글 목록 조회",
            description = "지정한 토론의 댓글 목록을 반환합니다."
    )
    public List<DiscussionDto.CommentResponse> getComments(@PathVariable Long discussionId) {
        return discussionQueryUseCase.findCommentsByDiscussionId(discussionId);
    }

    /**
     * 댓글을 작성합니다.
     */
    @PostMapping("/{discussionId}/comments")
    @Operation(
            summary = "댓글 작성",
            description = "인증된 사용자로 댓글을 생성합니다."
    )
    public DiscussionDto.CommentResponse addComment(
            @Parameter(description = "토론 ID")
            @PathVariable Long discussionId,
            @RequestBody DiscussionCommentDto.CreateRequest request,
            @Parameter(hidden = true)
            @AuthenticatedUser Requester requester) {
        return commentCommandUseCase.addComment(discussionId, requester.getUuid(), request.getContent());
    }

    /**
     * 댓글을 삭제합니다.
     */
    @DeleteMapping("/comments/{commentId}")
    @Operation(
            summary = "댓글 삭제",
            description = "인증된 사용자의 댓글을 삭제합니다."
    )
    public void deleteComment(
            @Parameter(description = "댓글 ID")
            @PathVariable Long commentId,
            @Parameter(hidden = true)
            @AuthenticatedUser Requester requester) {
        commentCommandUseCase.deleteComment(commentId, requester.getUuid());
    }

    /**
     * 댓글 좋아요를 토글합니다.
     */
    @PostMapping("/comments/{commentId}/like")
    @Operation(
            summary = "댓글 좋아요 토글",
            description = "인증된 사용자에 대해 좋아요를 토글합니다."
    )
    public void toggleCommentLike(
            @Parameter(description = "댓글 ID")
            @PathVariable Long commentId,
            @Parameter(hidden = true)
            @AuthenticatedUser Requester requester) {
        commentCommandUseCase.toggleCommentLike(commentId, requester.getUuid());
    }
}
