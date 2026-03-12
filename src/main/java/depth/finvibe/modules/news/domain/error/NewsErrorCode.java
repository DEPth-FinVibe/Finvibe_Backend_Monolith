package depth.finvibe.modules.news.domain.error;

import depth.finvibe.common.error.DomainErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum NewsErrorCode implements DomainErrorCode {

    NEWS_NOT_FOUND("NEWS_NOT_FOUND", "존재하지 않는 뉴스입니다.");

    private final String code;
    private final String message;
}
