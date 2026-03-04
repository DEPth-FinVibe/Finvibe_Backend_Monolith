package depth.finvibe.modules.user.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import depth.finvibe.modules.user.application.port.out.InterestStockRepository;
import depth.finvibe.modules.user.application.port.out.GamificationClient;
import depth.finvibe.modules.user.application.port.out.MarketClient;
import depth.finvibe.modules.user.application.port.out.UserEventPublisher;
import depth.finvibe.modules.user.application.port.out.UserRepository;
import depth.finvibe.modules.user.domain.vo.*;
import depth.finvibe.modules.user.infra.persistence.DailyLoginChecker;
import org.jspecify.annotations.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.user.application.port.in.UserCommandUseCase;
import depth.finvibe.modules.user.application.port.in.UserQueryUseCase;
import depth.finvibe.modules.user.domain.InterestStock;
import depth.finvibe.modules.user.domain.User;
import depth.finvibe.modules.user.domain.error.UserErrorCode;
import depth.finvibe.modules.user.dto.UserDto;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.common.user.error.DomainException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements UserCommandUseCase, UserQueryUseCase {

    private final UserRepository userRepository;
    private final InterestStockRepository interestStockRepository;
    private final GamificationClient gamificationClient;
    private final MarketClient marketClient;
    private final PasswordEncoder passwordEncoder;
    private final DailyLoginChecker dailyLoginChecker;
    private final UserEventPublisher userEventPublisher;

    @Override
    @Transactional
    public UserDto.UserResponse update(UserDto.UpdateUserRequest request, Requester requester) {
        User user = userRepository.findById(requester.getUserId())
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        checkLoginIdAlreadyExist(user, request.getLoginId());
        checkNicknameAlreadyExist(user, request.getNickname());

        updateUserAttributes(request, user, requester);

        return UserDto.UserResponse.from(user);
    }

    @Override
    @Transactional
    public UserDto.UserResponse changeNickname(UserDto.ChangeNicknameRequest request, Requester requester) {
        User user = userRepository.findById(requester.getUserId())
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        user.validateUpdatable(requester.getUserId(), requester.getRole());
        checkNicknameAlreadyExist(user, request.getNickname());

        PersonalDetails personalDetails = user.getPersonalDetails();
        PersonalDetails updatedPersonalDetails = PersonalDetails.of(
                personalDetails.getPhoneNumber(),
                personalDetails.getBirthDate(),
                personalDetails.getName(),
                request.getNickname(),
                personalDetails.getEmail()
        );

        user.update(null, null, updatedPersonalDetails);
        return UserDto.UserResponse.from(user);
    }

    @Override
    @Transactional
    public UserDto.UserResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        user.validateActive();

        // 당일 첫 로그인인 경우 LOGIN 메트릭 이벤트 발행
        checkAndPublishDailyLogin(userId);

        return UserDto.UserResponse.from(user);
    }

    /**
     * 당일 첫 로그인인지 확인하고 메트릭 이벤트를 발행합니다.
     */
    private void checkAndPublishDailyLogin(UUID userId) {
        boolean isFirstLoginToday = dailyLoginChecker.checkAndMarkDailyLogin(userId);

        if (isFirstLoginToday) {
            userEventPublisher.publishUserMetricEvent(
                    userId,
                    "LOGIN",
                    1.0,
                    Instant.now()
            );
        }
    }

    @Override
    @Transactional
    public UserDto.FavoriteStockResponse addFavoriteStock(Long stockId, Requester requester) {
        checkStockIsAlreadyAdded(requester.getUserId(), stockId);

        String stockName = marketClient.getStockNameByStockId(stockId)
                .orElseThrow(() -> new DomainException(UserErrorCode.MARKET_DATA_NOT_FOUND));

        InterestStock interestStock = InterestStock.create(requester.getUserId(), stockId, stockName);
        interestStock.validateCreatable(requester.getUserId(), requester.getRole());

        InterestStock saved = interestStockRepository.save(interestStock);

        return UserDto.FavoriteStockResponse.from(saved);
    }

    @Override
    @Transactional
    public UserDto.FavoriteStockResponse removeFavoriteStock(Long stockId, Requester requester) {
        InterestStock interestStock = interestStockRepository.findByUserIdAndStockId(requester.getUserId(), stockId)
                .orElseThrow(() -> new DomainException(UserErrorCode.INTEREST_STOCK_NOT_FOUND));

        interestStock.validateDeletable(requester.getUserId(), requester.getRole());

        interestStockRepository.deleteByUserIdAndStockId(requester.getUserId(), stockId);
        return UserDto.FavoriteStockResponse.from(interestStock);
    }

    @Override
    @Transactional
    public void withdraw(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        user.withdraw();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto.FavoriteStockResponse> getFavoriteStocks(UUID userId) {
        return interestStockRepository.findAllByUserId(userId).stream()
                .map(UserDto.FavoriteStockResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto.DuplicateCheckResponse checkLoginIdDuplicate(String loginId) {
        boolean isDuplicate = userRepository.existsByLoginId(new LoginId(loginId));
        return new UserDto.DuplicateCheckResponse(isDuplicate);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto.DuplicateCheckResponse checkEmailDuplicate(String email) {
        boolean isDuplicate = userRepository.existsByEmail(new Email(email));
        return new UserDto.DuplicateCheckResponse(isDuplicate);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto.DuplicateCheckResponse checkNicknameDuplicate(String nickname) {
        boolean isDuplicate = userRepository.existsByNickname(nickname);
        return new UserDto.DuplicateCheckResponse(isDuplicate);
    }

    @Override
    @Transactional(readOnly = true)
    public String getNickname(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));
        return user.getPersonalDetails().getNickname();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto.MemberProfileResponse getMemberProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        UserDto.UserSummaryResponse summary = gamificationClient.getUserSummary(userId)
                .orElseThrow(() -> new DomainException(UserErrorCode.GAMIFICATION_DATA_NOT_FOUND));

        return UserDto.MemberProfileResponse.builder()
                .userId(user.getId())
                .nickname(user.getPersonalDetails().getNickname())
                .gamificationSummary(summary)
                .build();
    }

    private void updateUserAttributes(UserDto.UpdateUserRequest request, User user, Requester requester) {
        user.validateUpdatable(requester.getUserId(), requester.getRole());

        LoginId updateLoginId = null;
        PasswordHash updatePasswordHash = null;
        PersonalDetails updatePersonalDetails;

        updateLoginId = createUpdateLoginId(request, updateLoginId);
        updatePasswordHash = createUpdatePasswordHash(request, user, updatePasswordHash);
        updatePersonalDetails = createUpdatedPersonalDetails(request, user);

        user.update(
            updateLoginId,
            updatePasswordHash,
            updatePersonalDetails
        );
    }

    private static LoginId createUpdateLoginId(UserDto.UpdateUserRequest request, LoginId updateLoginId) {
        if(Objects.nonNull(request.getLoginId())) {
            updateLoginId = new LoginId(request.getLoginId());
        }
        return updateLoginId;
    }

    private PasswordHash createUpdatePasswordHash(UserDto.UpdateUserRequest request, User user, PasswordHash updatePasswordHash) {
        if(Objects.nonNull(request.getNewPassword())) {
            boolean passwordMatch = user.getPasswordHash().matches(request.getOldPassword(), passwordEncoder);
            if (!passwordMatch) {
                throw new DomainException(UserErrorCode.INVALID_PASSWORD);
            }
            updatePasswordHash = PasswordHash.create(request.getNewPassword(), passwordEncoder);
        }
        return updatePasswordHash;
    }

    private static @NonNull PersonalDetails createUpdatedPersonalDetails(UserDto.UpdateUserRequest request, User user) {
        PersonalDetails updatePersonalDetails;
        updatePersonalDetails = PersonalDetails.of(
            Objects.nonNull(request.getPhoneNumber()) ? PhoneNumber.parse(request.getPhoneNumber()) : user.getPersonalDetails().getPhoneNumber(),
            Objects.nonNull(request.getBirthDate()) ? request.getBirthDate() : user.getPersonalDetails().getBirthDate(),
            Objects.nonNull(request.getNickname()) ? request.getNickname() : user.getPersonalDetails().getNickname(),
            Objects.nonNull(request.getName()) ? request.getName() : user.getPersonalDetails().getName(),
            Objects.nonNull(request.getEmail()) ? new Email(request.getEmail()) : user.getPersonalDetails().getEmail()
        );
        return updatePersonalDetails;
    }


    private void checkLoginIdAlreadyExist(User user, String newLoginId) {
        if (newLoginId != null && !user.getLoginId().getValue().equals(newLoginId)) {
            if (userRepository.existsByLoginId(new LoginId(newLoginId))) {
                throw new DomainException(UserErrorCode.LOGIN_ID_ALREADY_EXISTS);
            }
        }
    }

    private void checkNicknameAlreadyExist(User user, String newNickname) {
        if (newNickname != null && !user.getPersonalDetails().getNickname().equals(newNickname)) {
            if (userRepository.existsByNickname(newNickname)) {
                throw new DomainException(UserErrorCode.NICKNAME_ALREADY_EXISTS);
            }
        }
    }

    private void checkStockIsAlreadyAdded(UUID userId, Long stockId) {
        if (interestStockRepository.findByUserIdAndStockId(userId, stockId).isPresent()) {
            throw new DomainException(UserErrorCode.INTEREST_STOCK_ALREADY_EXISTS);
        }
    }
}
