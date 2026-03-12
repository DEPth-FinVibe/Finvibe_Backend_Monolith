package depth.finvibe.modules.news.domain.error;

import depth.finvibe.common.error.DomainErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ThemeErrorCode implements DomainErrorCode {
    THEME_NOT_FOUND("THEME_NOT_FOUND", "오늘의 테마를 찾을 수 없습니다.");

    private final String code;
    private final String message;
}
