package depth.finvibe.common.insight.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class DomainException extends RuntimeException {

    /**
     * 발생한 에러의 세부 정보를 담고 있는 도메인 에러 코드입니다.
     */
    private final DomainErrorCode errorCode;

}

