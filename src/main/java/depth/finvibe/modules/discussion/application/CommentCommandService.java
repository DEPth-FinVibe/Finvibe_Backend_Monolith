package depth.finvibe.modules.discussion.application;

import depth.finvibe.modules.discussion.application.port.in.CommentCommandUseCase;
import depth.finvibe.modules.discussion.application.port.out.DiscussionCommentLikeRepository;
import depth.finvibe.modules.discussion.application.port.out.DiscussionCommentRepository;
import depth.finvibe.modules.discussion.application.port.out.DiscussionRepository;
import depth.finvibe.modules.discussion.domain.Discussion;
import depth.finvibe.modules.discussion.domain.DiscussionComment;
import depth.finvibe.modules.discussion.domain.DiscussionCommentLike;
import depth.finvibe.modules.discussion.domain.error.DiscussionErrorCode;
import depth.finvibe.modules.discussion.dto.DiscussionDto;
import depth.finvibe.common.insight.application.port.out.UserMetricEventPort;
import depth.finvibe.common.insight.dto.MetricEventType;
import depth.finvibe.common.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentCommandService implements CommentCommandUseCase {

    private final DiscussionRepository discussionRepository;
    private final DiscussionCommentRepository discussionCommentRepository;
    private final DiscussionCommentLikeRepository discussionCommentLikeRepository;
    private final UserMetricEventPort userMetricEventPort;

    @Override
    public DiscussionDto.CommentResponse addComment(Long discussionId, UUID userId, String content) {
        Discussion discussion = discussionRepository.findById(discussionId)
                .orElseThrow(() -> new DomainException(DiscussionErrorCode.DISCUSSION_NOT_FOUND));

        DiscussionComment comment = DiscussionComment.create(discussion, userId, content);
        DiscussionComment saved = discussionCommentRepository.save(comment);
        userMetricEventPort.publish(
                userId.toString(),
                MetricEventType.DISCUSSION_COMMENT_COUNT,
                1.0,
                Instant.now());

        return mapToCommentResponse(saved);
    }

    @Override
    public DiscussionDto.CommentResponse updateComment(Long commentId, UUID userId, String content) {
        DiscussionComment comment = discussionCommentRepository.findById(commentId)
                .orElseThrow(() -> new DomainException(DiscussionErrorCode.DISCUSSION_NOT_FOUND));

        if (!comment.getUserId().equals(userId)) {
            throw new DomainException(DiscussionErrorCode.DISCUSSION_NOT_FOUND);
        }

        comment.updateContent(content);
        DiscussionComment updated = discussionCommentRepository.save(comment);

        return mapToCommentResponse(updated);
    }

    @Override
    public void deleteComment(Long commentId, UUID userId) {
        DiscussionComment comment = discussionCommentRepository.findById(commentId)
                .orElseThrow(() -> new DomainException(DiscussionErrorCode.DISCUSSION_NOT_FOUND));

        if (!comment.getUserId().equals(userId)) {
            throw new DomainException(DiscussionErrorCode.DISCUSSION_NOT_FOUND);
        }

        discussionCommentLikeRepository.deleteByCommentId(commentId);
        discussionCommentRepository.delete(comment);
    }

    @Override
    public void toggleCommentLike(Long commentId, UUID userId) {
        discussionCommentLikeRepository.findByCommentIdAndUserId(commentId, userId)
                .ifPresentOrElse(
                        discussionCommentLikeRepository::delete,
                        () -> {
                            DiscussionComment comment = discussionCommentRepository.findById(commentId)
                                    .orElseThrow(() -> new DomainException(DiscussionErrorCode.COMMENT_NOT_FOUND));
                            discussionCommentLikeRepository.save(DiscussionCommentLike.create(comment, userId));
                        });
    }

    private DiscussionDto.CommentResponse mapToCommentResponse(DiscussionComment comment) {
        long likeCount = discussionCommentLikeRepository.countByCommentId(comment.getId());
        return new DiscussionDto.CommentResponse(comment, likeCount);
    }
}
