package depth.finvibe.modules.gamification.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum UserMetricType {
    LOGIN_COUNT_PER_DAY(
            "접속 횟수 (1일당 1회)",
            "로그인 횟수 누적(로그인 이벤트 발생 시 기본 +1)",
            true
    ),
    CURRENT_RETURN_RATE(
            "현재 수익률",
            "현재 수익률(누적 아님, 최신 값을 그대로 반영)",
            false
    ),
    STOCK_BUY_COUNT(
            "주식 구매 횟수",
            "주식 매수 횟수 누적",
            true
    ),
    STOCK_SELL_COUNT(
            "주식 판매 횟수",
            "주식 매도 횟수 누적",
            true
    ),
    PORTFOLIO_COUNT_WITH_STOCKS(
            "포트폴리오 개수 (주식이 있는 것만)",
            "주식 보유 포트폴리오 개수(누적 아님, 최신 값)",
            false
    ),
    HOLDING_STOCK_COUNT(
            "보유 종목 개수",
            "보유 종목 수(누적 아님, 최신 값)",
            false
    ),
    NEWS_COMMENT_COUNT(
            "뉴스에 달린 댓글 수",
            "뉴스 댓글 작성 횟수 누적",
            true
    ),
    NEWS_LIKE_COUNT(
            "뉴스에 남긴 좋아요 수",
            "뉴스 좋아요 누른 횟수 누적",
            true
    ),
    DISCUSSION_POST_COUNT(
            "새 토론 게시글 횟수",
            "토론 글 작성 횟수 누적",
            true
    ),
    DISCUSSION_COMMENT_COUNT(
            "토론에 달린 댓글 횟수",
            "토론 댓글 작성 횟수 누적",
            true
    ),
    DISCUSSION_LIKE_COUNT(
            "토론에 좋아요를 누른 횟수",
            "토론 좋아요 누른 횟수 누적",
            true
    ),
    AI_CONTENT_COMPLETE_COUNT(
            "AI 투자자 콘텐츠 완료 횟수",
            "AI 콘텐츠 완료 횟수 누적",
            true
    ),
    CHALLENGE_COMPLETION_COUNT(
            "챌린지 달성 횟수",
            "챌린지 달성 횟수 누적",
            true
    ),
    LOGIN_STREAK_DAYS(
            "연속 접속 일수",
            "연속 로그인 일수(같은 날 재로그인 시 증가 없음, 끊기면 1로 재시작)",
            false
    ),
    LAST_LOGIN_DATETIME(
            "최근 접속 일시",
            "마지막 로그인 시각(epoch milli, 내부 상태성 지표)",
            false
    );

    private final String description;
    private final String llmDescription;
    private final boolean weeklyCollect;
}
