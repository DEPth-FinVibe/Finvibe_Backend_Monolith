package depth.finvibe.modules.user.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import depth.finvibe.common.error.DomainException;
import depth.finvibe.modules.user.domain.error.UserErrorCode;

/**
 * 가입 시 생년월일·휴대폰 번호를 받지 않으므로 두 값 없이도 회원이 만들어져야 한다.
 */
class PersonalDetailsOptionalFieldsTest {

    @Test
    @DisplayName("생년월일과 휴대폰 번호가 없어도 개인정보를 만들 수 있다")
    void of_withoutBirthDateAndPhoneNumber_succeeds() {
        PersonalDetails details = PersonalDetails.of(
                null, null, "홍길동", "핀바이브", new Email("user@example.com"));

        assertThat(details.getBirthDate()).isNull();
        assertThat(details.getPhoneNumber()).isNull();
        assertThat(details.getName()).isEqualTo("홍길동");
        assertThat(details.getNickname()).isEqualTo("핀바이브");
    }

    @Test
    @DisplayName("생년월일이 들어오면 여전히 범위를 검증한다")
    void of_withInvalidBirthDate_throws() {
        LocalDate future = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> PersonalDetails.of(
                null, future, "홍길동", "핀바이브", new Email("user@example.com")))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_BIRTH_DATE);
    }

    @Test
    @DisplayName("정상 생년월일은 그대로 통과한다")
    void of_withValidBirthDate_succeeds() {
        LocalDate birthDate = LocalDate.of(1990, 1, 1);

        assertThatCode(() -> PersonalDetails.of(
                null, birthDate, "홍길동", "핀바이브", new Email("user@example.com")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("휴대폰 번호가 비었으면 null로 처리한다")
    void parseNullable_blankOrNull_returnsNull() {
        assertThat(PhoneNumber.parseNullable(null)).isNull();
        assertThat(PhoneNumber.parseNullable("")).isNull();
        assertThat(PhoneNumber.parseNullable("   ")).isNull();
    }

    @Test
    @DisplayName("휴대폰 번호가 들어오면 여전히 형식을 검증한다")
    void parseNullable_malformed_throws() {
        assertThatThrownBy(() -> PhoneNumber.parseNullable("01012345678"))
                .isInstanceOf(DomainException.class);

        assertThat(PhoneNumber.parseNullable("010-1234-5678").toString())
                .isEqualTo("010-1234-5678");
    }
}
