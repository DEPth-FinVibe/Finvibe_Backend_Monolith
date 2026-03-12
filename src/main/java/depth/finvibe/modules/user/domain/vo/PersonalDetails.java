package depth.finvibe.modules.user.domain.vo;

import depth.finvibe.modules.user.domain.error.UserErrorCode;
import depth.finvibe.common.error.DomainException;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class PersonalDetails {
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "firstPart", column = @Column(name = "phone_number_first_part")),
        @AttributeOverride(name = "secondPart", column = @Column(name = "phone_number_second_part")),
        @AttributeOverride(name = "thirdPart", column = @Column(name = "phone_number_third_part"))
    })
    private PhoneNumber phoneNumber;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String nickname;

    @Embedded
    private Email email;

    public static PersonalDetails of(PhoneNumber phoneNumber, LocalDate birthDate, String name, String nickname, Email email) {
        validateBirthday(birthDate);
        validateNickname(nickname);
        validateName(name);

        return new PersonalDetails(
                phoneNumber,
                birthDate,
                name,
                nickname,
                email
        );
    }

    private static void validateName(String name) {
        //이름은 2자 이상 10자 이하의 영문 대소문자, 한글만 허용
        if (name == null || !name.matches("^[a-zA-Z가-힣]{2,10}$")) {
            throw new DomainException(UserErrorCode.INVALID_NAME_FORMAT);
        }
    }

    private static void validateNickname(String nickname) {
        //닉네임은 최대 10자, 특수문자 없이 영문/숫자/한글만 허용
        if (nickname == null || !nickname.matches("^[a-zA-Z0-9가-힣]{1,10}$")) {
            throw new DomainException(UserErrorCode.INVALID_NICKNAME_FORMAT);
        }
    }

    private static void validateBirthday(LocalDate birthDate) {
        //120세 이상, 미래에서 온 생일 방지
        LocalDate today = LocalDate.now();
        LocalDate earliestValidDate = today.minusYears(120);
        if (birthDate.isBefore(earliestValidDate) || birthDate.isAfter(today)) {
            throw new DomainException(UserErrorCode.INVALID_BIRTH_DATE);
        }
    }
}
