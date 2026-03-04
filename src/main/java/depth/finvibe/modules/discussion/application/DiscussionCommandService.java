package depth.finvibe.modules.discussion.application;

import depth.finvibe.modules.discussion.application.port.in.DiscussionCommandUseCase;
import depth.finvibe.modules.discussion.application.port.out.DiscussionCommentLikeRepository;
import depth.finvibe.modules.discussion.application.port.out.DiscussionCommentRepository;
import depth.finvibe.modules.discussion.application.port.out.DiscussionEventPort;
import depth.finvibe.modules.discussion.application.port.out.DiscussionLikeRepository;
import depth.finvibe.modules.discussion.application.port.out.DiscussionRepository;
import depth.finvibe.modules.discussion.domain.Discussion;
import depth.finvibe.modules.discussion.domain.DiscussionComment;
import depth.finvibe.modules.discussion.domain.DiscussionLike;
import depth.finvibe.modules.discussion.domain.error.DiscussionErrorCode;
import depth.finvibe.modules.discussion.dto.DiscussionDto;
import depth.finvibe.common.insight.application.port.out.UserMetricEventPort;
import depth.finvibe.common.insight.dto.MetricEventType;
import depth.finvibe.common.insight.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class DiscussionCommandService implements DiscussionCommandUseCase {

    private final DiscussionRepository discussionRepository;
    private final DiscussionCommentRepository discussionCommentRepository;
    private final DiscussionCommentLikeRepository discussionCommentLikeRepository;
    private final DiscussionLikeRepository discussionLikeRepository;
    private final DiscussionEventPort discussionEventPort;
    private final UserMetricEventPort userMetricEventPort;

    @Override
    public DiscussionDto.Response addDiscussion(Long newsId, UUID userId, String content) {
        // DB 분리 환경에서는 newsId의 유효성 검증을 직접 하지 않거나 별도 통신(REST/gRPC)을 사용함.
        // 여기서는 newsId를 그대로 저장하고 이벤트를 발행하는 것에 집중.
        Discussion discussion = Discussion.create(newsId, userId, content);
        Discussion saved = discussionRepository.save(discussion);

        MetricEventType metricEventType = newsId != null
                ? MetricEventType.DISCUSSION_CREATED
                : MetricEventType.DISCUSSION_POST_COUNT;
        userMetricEventPort.publish(userId.toString(), metricEventType, 1.0, Instant.now());

        if (newsId != null) {
            discussionEventPort.publishCreated(newsId);
        }

        return mapToDiscussionResponse(saved);
    }

    @Override
    public DiscussionDto.Response updateDiscussion(Long discussionId, UUID userId, String content) {
        Discussion discussion = discussionRepository.findById(discussionId)
                .orElseThrow(() -> new DomainException(DiscussionErrorCode.DISCUSSION_NOT_FOUND));

        // 작성자만 수정 가능
        if (!discussion.getUserId().equals(userId)) {
            throw new DomainException(DiscussionErrorCode.DISCUSSION_NOT_FOUND);
        }

        discussion.updateContent(content);
        Discussion updated = discussionRepository.save(discussion);

        return mapToDiscussionResponse(updated);
    }

    @Override
    public void deleteDiscussion(Long discussionId, UUID userId) {
        Discussion discussion = discussionRepository.findById(discussionId)
                .orElseThrow(() -> new DomainException(DiscussionErrorCode.DISCUSSION_NOT_FOUND));

        // 작성자만 삭제 가능
        if (!discussion.getUserId().equals(userId)) {
            throw new DomainException(DiscussionErrorCode.DISCUSSION_NOT_FOUND);
        }

        discussionCommentLikeRepository.deleteByDiscussionId(discussionId);
        discussionLikeRepository.deleteByDiscussionId(discussionId);
        discussionCommentRepository.deleteByDiscussionId(discussionId);
        discussionRepository.delete(discussion);

        Long newsId = discussion.getNewsId();
        if (newsId != null) {
            discussionEventPort.publishDeleted(newsId);
        }
    }

    @Override
    public void toggleDiscussionLike(Long discussionId, UUID userId) {
        discussionLikeRepository.findByDiscussionIdAndUserId(discussionId, userId)
                .ifPresentOrElse(
                        existingLike -> {
                            discussionLikeRepository.delete(existingLike);
                            userMetricEventPort.publish(
                                    userId.toString(),
                                    MetricEventType.DISCUSSION_LIKE_COUNT,
                                    -1.0,
                                    Instant.now());
                        },
                        () -> {
                            Discussion discussion = discussionRepository.findById(discussionId)
                                    .orElseThrow(() -> new DomainException(DiscussionErrorCode.DISCUSSION_NOT_FOUND));
                            discussionLikeRepository.save(DiscussionLike.create(discussion, userId));
                            userMetricEventPort.publish(
                                    userId.toString(),
                                    MetricEventType.DISCUSSION_LIKE_COUNT,
                                    1.0,
                                    Instant.now());
                        });
    }

    private DiscussionDto.Response mapToDiscussionResponse(Discussion discussion) {
        long likeCount = discussionLikeRepository.countByDiscussionId(discussion.getId());
        List<DiscussionComment> comments = discussionCommentRepository
                .findAllByDiscussionIdOrderByCreatedAtAsc(discussion.getId());

        List<DiscussionDto.CommentResponse> commentDtos = comments.stream()
                .map(this::mapToCommentResponse)
                .toList();

        return new DiscussionDto.Response(discussion, likeCount, commentDtos);
    }

    private DiscussionDto.CommentResponse mapToCommentResponse(DiscussionComment comment) {
        long likeCount = discussionCommentLikeRepository.countByCommentId(comment.getId());
        return new DiscussionDto.CommentResponse(comment, likeCount);
    }
}
