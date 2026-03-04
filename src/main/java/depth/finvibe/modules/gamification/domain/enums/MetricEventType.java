package depth.finvibe.modules.gamification.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MetricEventType {
    LOGIN("로그인"),
    AI_CONTENT_COMPLETED("AI 투자자 콘텐츠 수료"),
    HOLDING_STOCK_COUNT_CHANGED("보유 종목 개수 변경"),
    CHALLENGE_COMPLETED("챌린지 완료"),
    CURRENT_RETURN_RATE_UPDATED("현재 수익률 변경"),
    STOCK_BOUGHT("주식 구매"),
    STOCK_SOLD("주식 판매"),
    PORTFOLIO_WITH_STOCKS_COUNT_CHANGED("주식 보유 포트폴리오 개수 변경"),
    NEWS_COMMENT_CREATED("뉴스 댓글 작성"),
    NEWS_LIKED("뉴스 좋아요"),
    DISCUSSION_POST_CREATED("토론 게시글 작성"),
    DISCUSSION_COMMENT_CREATED("토론 댓글 작성"),
    DISCUSSION_LIKED("토론 좋아요");

    private final String description;
}
