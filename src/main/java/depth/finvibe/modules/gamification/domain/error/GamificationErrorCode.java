package depth.finvibe.modules.gamification.domain.error;

import depth.finvibe.common.error.DomainErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum GamificationErrorCode implements DomainErrorCode {

    // ===== XP 관련 에러 =====
    INVALID_XP_VALUE("GAMIFICATION_INVALID_XP_VALUE", "xp의 값은 0보다 커야 합니다."),
    INVALID_XP_REASON("GAMIFICATION_INVALID_XP_REASON", "xp의 지급 사유는 비어 있을 수 없습니다."),

    // ===== 기간(Period) 관련 에러 =====
    INVALID_PERIOD_START_DATE_OR_END_DATE("GAMIFICATION_INVALID_PERIOD_START_DATE_OR_END_DATE", "period의 시작일 또는 종료일이 비어 있을 수 없습니다."),
    INVALID_PERIOD_START_DATE_IS_GREATER_THAN_END_DATE("GAMIFICATION_INVALID_PERIOD_START_DATE_IS_GREATER_THAN_END_DATE", "period의 시작일이 종료일보다 클 수 없습니다."),

    // ===== 메트릭(Metric) 관련 에러 =====
    INVALID_METRIC_TYPE("GAMIFICATION_INVALID_METRIC_TYPE", "metricType은 비어 있을 수 없습니다."),
    INVALID_METRIC_DELTA("GAMIFICATION_INVALID_METRIC_DELTA", "metric delta는 비어 있을 수 없습니다."),

    // ===== 목표값(Target, Reward) 관련 에러 =====
    INVALID_TARGET_VALUE("GAMIFICATION_INVALID_TARGET_VALUE", "targetValue는 0보다 커야 합니다."),
    INVALID_REWARD_XP("GAMIFICATION_INVALID_REWARD_XP", "rewardXp는 0보다 커야 합니다."),
    INVALID_REWARD_BADGE("GAMIFICATION_INVALID_REWARD_BADGE", "rewardBadge는 비어 있을 수 없습니다."),

    // ===== 개인 도전과제(PersonalChallenge) 관련 에러 =====
    INVALID_PERSONAL_CHALLENGE_TITLE_IS_EMPTY("GAMIFICATION_INVALID_PERSONAL_CHALLENGE_TITLE_IS_EMPTY", "개인 도전과제의 제목은 비어 있을 수 없습니다."),
    INVALID_PERSONAL_CHALLENGE_ID("GAMIFICATION_INVALID_PERSONAL_CHALLENGE_ID", "올바르지 않은 개인 도전과제 ID입니다."),
    INVALID_USER_ID("GAMIFICATION_INVALID_USER_ID", "올바르지 않은 사용자 ID입니다."),
    INVALID_PERIOD("GAMIFICATION_INVALID_PERIOD", "period는 비어 있을 수 없습니다."),
    INVALID_REWARD("GAMIFICATION_INVALID_REWARD", "reward는 비어 있을 수 없습니다."),

    // ===== 뱃지(Badge) 관련 에러 =====
    BADGE_ALREADY_EXIST("GAMIFICATION_BADGE_ALREADY_EXIST", "이미 보유한 뱃지입니다."),

    // ===== 스쿼드(Squad) 관련 에러 =====
    SQUAD_NOT_FOUND("GAMIFICATION_SQUAD_NOT_FOUND", "존재하지 않는 스쿼드입니다."),
    SQUAD_NAME_IS_EMPTY("GAMIFICATION_SQUAD_NAME_IS_EMPTY", "스쿼드 이름은 비어 있을 수 없습니다."),
    SQUAD_REGION_IS_EMPTY("GAMIFICATION_SQUAD_REGION_IS_EMPTY", "스쿼드 지역은 비어 있을 수 없습니다."),
    USER_SQUAD_NOT_FOUND("GAMIFICATION_USER_SQUAD_NOT_FOUND", "사용자가 속한 스쿼드를 찾을 수 없습니다."),
    FORBIDDEN_ACCESS("GAMIFICATION_FORBIDDEN_ACCESS", "관리자만 접근 가능합니다.");

    private final String code;
    private final String message;
}
