package depth.finvibe.modules.news.dto;

import depth.finvibe.modules.news.domain.EconomicSignal;
import depth.finvibe.modules.news.domain.News;
import depth.finvibe.modules.news.domain.NewsKeyword;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NewsDto {

    @Getter
    @NoArgsConstructor
    public static class QueryRequest {
        private NewsSortType sort = NewsSortType.LATEST;
    }

    /**
     * 뉴스 목록/상세 카드에서 공통으로 사용하는 요약 정보 응답 DTO.
     */
    @Getter
    public static class Response {
        private final Long id;
        private final String title;
        private final EconomicSignal economicSignal;
        private final NewsKeyword keyword;
        private final String analysis;
        private final long likeCount;
        private final long discussionCount;
        /**
         * 공유 수는 아직 별도 저장소가 없으므로 0으로 내려주며,
         * 이후 인프라가 준비되면 실제 값으로 대체할 수 있습니다.
         */
        private final long shareCount;
        private final LocalDateTime createdAt;

        public Response(News news, long likeCount, long discussionCount, long shareCount) {
            this.id = news.getId();
            this.title = news.getTitle();
            this.economicSignal = news.getEconomicSignal();
            this.keyword = news.getKeyword();
            this.analysis = news.getAnalysis();
            this.likeCount = likeCount;
            this.discussionCount = discussionCount;
            this.shareCount = shareCount;
            this.createdAt = news.getCreatedAt();
        }
    }

    /**
     * 뉴스 상세 화면 응답 DTO.
     * 토론 목록은 별도 API에서 조회하고, 이 DTO에는 카운트 정보만 포함합니다.
     */
    @Getter
    public static class DetailResponse {
        private final Long id;
        private final String title;
        private final String content;
        private final String analysis;
        private final EconomicSignal economicSignal;
        private final NewsKeyword keyword;
        private final long likeCount;
        private final long discussionCount;
        private final long shareCount;
        private final LocalDateTime createdAt;

        public DetailResponse(News news, long likeCount, long discussionCount, long shareCount) {
            this.id = news.getId();
            this.title = news.getTitle();
            this.content = news.getContent();
            this.analysis = news.getAnalysis();
            this.economicSignal = news.getEconomicSignal();
            this.keyword = news.getKeyword();
            this.likeCount = likeCount;
            this.discussionCount = discussionCount;
            this.shareCount = shareCount;
            this.createdAt = news.getCreatedAt();
        }
    }

    /**
     * 뉴스 목록용 페이지 응답 DTO.
     */
    @Getter
    public static class ListResponse {
        private final List<Response> items;
        private final int page;
        private final int size;
        private final boolean hasNext;

        public ListResponse(List<Response> items, int page, int size, boolean hasNext) {
            this.items = items;
            this.page = page;
            this.size = size;
            this.hasNext = hasNext;
        }
    }

    @Getter
    public static class KeywordTrendResponse {
        private final NewsKeyword keyword;
        private final long count;

        public KeywordTrendResponse(NewsKeyword keyword, long count) {
            this.keyword = keyword;
            this.count = count;
        }
    }
}
