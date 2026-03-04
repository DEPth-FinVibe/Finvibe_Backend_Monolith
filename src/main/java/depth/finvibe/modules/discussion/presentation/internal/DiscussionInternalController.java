package depth.finvibe.modules.discussion.presentation.internal;

import depth.finvibe.modules.discussion.application.port.in.DiscussionQueryUseCase;
import depth.finvibe.modules.discussion.dto.DiscussionDto;
import depth.finvibe.modules.discussion.dto.DiscussionSortType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/discussions")
@RequiredArgsConstructor
@Tag(name = "토론 내부", description = "내부 토론 API")
public class DiscussionInternalController {

    private final DiscussionQueryUseCase discussionQueryUseCase;

    /**
     * 특정 뉴스의 토론 목록을 조회합니다.
     */
    @GetMapping
    @Operation(
            summary = "뉴스별 토론 목록 조회",
            description = "특정 뉴스의 토론 목록을 반환합니다."
    )
    public List<DiscussionDto.Response> getDiscussions(
            @RequestBody DiscussionDto.QueryRequest request) {
        DiscussionSortType sortType = request.getSort() == null ? DiscussionSortType.LATEST : request.getSort();
        return discussionQueryUseCase.findAllByNewsId(request.getNewsId(), sortType);
    }

    /**
     * 여러 뉴스의 토론 수를 벌크로 조회합니다.
     * 분산 환경에서 News 모듈이 HTTP로 호출하는 엔드포인트입니다.
     */
    @GetMapping("/counts")
    @Operation(
            summary = "토론 수 벌크 조회",
            description = "news id별 토론 수 맵을 반환합니다."
    )
    public Map<Long, Long> getDiscussionCounts(@RequestBody DiscussionDto.CountQueryRequest request) {
        return discussionQueryUseCase.countByNewsIds(request.getNewsIds());
    }
}
