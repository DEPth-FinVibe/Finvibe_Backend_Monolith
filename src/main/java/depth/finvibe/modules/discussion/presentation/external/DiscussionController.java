package depth.finvibe.modules.discussion.presentation.external;

import depth.finvibe.modules.discussion.application.port.in.DiscussionCommandUseCase;
import depth.finvibe.modules.discussion.application.port.in.DiscussionQueryUseCase;
import depth.finvibe.modules.discussion.dto.DiscussionDto;
import depth.finvibe.modules.discussion.dto.DiscussionSortType;
import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/discussions")
@RequiredArgsConstructor
@Tag(name = "토론", description = "토론 스레드")
public class DiscussionController {

    private final DiscussionQueryUseCase discussionQueryUseCase;
    private final DiscussionCommandUseCase discussionCommandUseCase;

    /**
     * 전체 토론 목록을 조회합니다.
     */
    @GetMapping
    @Operation(
            summary = "토론 목록 조회",
            description = "LATEST 또는 POPULAR 기준으로 토론 목록을 반환합니다."
    )
    public List<DiscussionDto.Response> getDiscussions(
            @RequestBody(required = false) DiscussionDto.QueryRequest request) {
        if (request == null) {
            request = new DiscussionDto.QueryRequest();
        }

        DiscussionSortType sortType = request.getSort() == null ? DiscussionSortType.LATEST : request.getSort();

        if (request.getNewsId() != null) {
            return discussionQueryUseCase.findAllByNewsId(request.getNewsId(), sortType);
        }

        return discussionQueryUseCase.findAll(sortType);
    }

    /**
     * 새로운 토론을 작성합니다.
     */
    @PostMapping
    @Operation(
            summary = "토론 작성",
            description = "새 토론 스레드를 생성합니다."
    )
    public DiscussionDto.Response createDiscussion(
            @RequestBody DiscussionDto.CreateRequest request,
            @Parameter(hidden = true)
            @AuthenticatedUser Requester requester) {
        return discussionCommandUseCase.addDiscussion(request.getNewsId(), requester.getUuid(), request.getContent());
    }

    /**
     * 토론을 삭제합니다.
     */
    @DeleteMapping("/{discussionId}")
    @Operation(
            summary = "토론 삭제",
            description = "작성자인 경우 토론 스레드를 삭제합니다."
    )
    public void deleteDiscussion(
            @Parameter(description = "토론 ID")
            @PathVariable Long discussionId,
            @Parameter(hidden = true)
            @AuthenticatedUser Requester requester) {
        discussionCommandUseCase.deleteDiscussion(discussionId, requester.getUuid());
    }

    /**
     * 토론 좋아요를 토글합니다.
     */
    @PostMapping("/{discussionId}/like")
    @Operation(
            summary = "토론 좋아요 토글",
            description = "해당 사용자에 대해 좋아요를 토글합니다."
    )
    public void toggleLike(
            @Parameter(description = "토론 ID")
            @PathVariable Long discussionId,
            @Parameter(hidden = true)
            @AuthenticatedUser Requester requester) {
        discussionCommandUseCase.toggleDiscussionLike(discussionId, requester.getUuid());
    }
}
