package depth.finvibe.common.error;

import lombok.Getter;

@Getter
public class DomainException extends RuntimeException {

	private final DomainErrorCode errorCode;

	public DomainException(DomainErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
}
