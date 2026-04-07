package depth.finvibe.modules.discussion.application;

import depth.finvibe.modules.discussion.application.port.in.DiscussionQueryUseCase;
import depth.finvibe.modules.discussion.application.port.out.DiscussionCommentLikeRepository;
import depth.finvibe.modules.discussion.application.port.out.DiscussionCommentRepository;
import depth.finvibe.modules.discussion.application.port.out.DiscussionLikeRepository;
import depth.finvibe.modules.discussion.application.port.out.DiscussionRepository;
import depth.finvibe.modules.discussion.domain.Discussion;
import depth.finvibe.modules.discussion.domain.DiscussionComment;
import depth.finvibe.modules.discussion.dto.DiscussionDto;
import depth.finvibe.modules.discussion.dto.DiscussionSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscussionQueryService implements DiscussionQueryUseCase {
    private static final int MAX_DISCUSSION_QUERY_SIZE = 500;

    private final DiscussionRepository discussionRepository;
    private final DiscussionCommentRepository discussionCommentRepository;
    private final DiscussionCommentLikeRepository discussionCommentLikeRepository;
    private final DiscussionLikeRepository discussionLikeRepository;

    @Override
    public long countByNewsId(Long newsId) {
        return discussionRepository.countByNewsId(newsId);
    }

    @Override
    public java.util.Map<Long, Long> countByNewsIds(java.util.List<Long> newsIds) {
        return discussionRepository.countByNewsIds(newsIds);
    }

    @Override
    public List<DiscussionDto.Response> findAllByNewsId(Long newsId, DiscussionSortType sortType) {
        List<DiscussionDto.Response> responses = discussionRepository
                .findAllByNewsIdOrderByCreatedAtAsc(newsId).stream()
                .map(this::mapToDiscussionResponse)
                .toList();

        if (sortType == DiscussionSortType.POPULAR) {
            return responses.stream()
                    .sorted(Comparator.comparingLong(DiscussionDto.Response::getLikeCount).reversed())
                    .toList();
        }

        return responses.stream()
                .sorted(Comparator.comparing(DiscussionDto.Response::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public List<DiscussionDto.Response> findAll(DiscussionSortType sortType) {
        List<Discussion> discussions = discussionRepository.findAllOrderByCreatedAtDesc(MAX_DISCUSSION_QUERY_SIZE);

        if (sortType == DiscussionSortType.LATEST) {
            return discussions.stream()
                    .map(this::mapToDiscussionResponse)
                    .toList();
        }

        return discussions.stream()
                .map(this::mapToDiscussionResponse)
                .sorted(Comparator.comparingLong(DiscussionDto.Response::getLikeCount).reversed())
                .toList();
    }

    @Override
    public List<DiscussionDto.CommentResponse> findCommentsByDiscussionId(Long discussionId) {
        return discussionCommentRepository.findAllByDiscussionIdOrderByCreatedAtAsc(discussionId).stream()
                .map(comment -> new DiscussionDto.CommentResponse(
                        comment, discussionCommentLikeRepository.countByCommentId(comment.getId())))
                .toList();
    }

    private DiscussionDto.Response mapToDiscussionResponse(Discussion discussion) {
        long likeCount = discussionLikeRepository.countByDiscussionId(discussion.getId());
        List<DiscussionComment> comments = discussionCommentRepository
                .findAllByDiscussionIdOrderByCreatedAtAsc(discussion.getId());

        List<DiscussionDto.CommentResponse> commentDtos = comments.stream()
                .map(comment -> new DiscussionDto.CommentResponse(
                        comment, discussionCommentLikeRepository.countByCommentId(comment.getId())))
                .toList();

        return new DiscussionDto.Response(discussion, likeCount, commentDtos);
    }
}
