package depth.finvibe.modules.user.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import depth.finvibe.boot.security.Requester;
import depth.finvibe.common.error.DomainException;
import depth.finvibe.modules.user.application.port.in.AuthCommandUseCase;
import depth.finvibe.modules.user.application.port.out.TemporaryTokenProvider;
import depth.finvibe.modules.user.application.port.out.TemporaryTokenResolver;
import depth.finvibe.modules.user.application.port.out.TokenFamilyCacheRepository;
import depth.finvibe.modules.user.application.port.out.TokenFamilyRepository;
import depth.finvibe.modules.user.application.port.out.TokenProvider;
import depth.finvibe.modules.user.application.port.out.TokenResolver;
import depth.finvibe.modules.user.application.port.out.UserEventPublisher;
import depth.finvibe.modules.user.application.port.out.UserRepository;
import depth.finvibe.modules.user.domain.AuthTokenClaims;
import depth.finvibe.modules.user.domain.LoginContext;
import depth.finvibe.modules.user.domain.TokenFamily;
import depth.finvibe.modules.user.domain.User;
import depth.finvibe.modules.user.domain.enums.AuthTokenType;
import depth.finvibe.modules.user.domain.error.UserErrorCode;
import depth.finvibe.modules.user.domain.vo.Email;
import depth.finvibe.modules.user.domain.vo.LoginId;
import depth.finvibe.modules.user.domain.vo.OAuthInfo;
import depth.finvibe.modules.user.domain.vo.PasswordHash;
import depth.finvibe.modules.user.domain.vo.PersonalDetails;
import depth.finvibe.modules.user.domain.vo.PhoneNumber;
import depth.finvibe.modules.user.dto.UserDto;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService implements AuthCommandUseCase {

	private final UserRepository userRepository;
	private final UserEventPublisher userEventPublisher;
	private final TokenProvider tokenProvider;
	private final TokenResolver tokenResolver;
	private final TokenFamilyRepository tokenFamilyRepository;
	private final TokenFamilyCacheRepository tokenFamilyCacheRepository;
	private final PasswordEncoder passwordEncoder;
	private final TemporaryTokenProvider temporaryTokenProvider;
	private final TemporaryTokenResolver temporaryTokenResolver;
	private final MeterRegistry meterRegistry;

	@Override
	@Transactional
	public UserDto.SignUpResponse signUp(UserDto.SignUpRequest request, LoginContext loginContext) {
		User savedUser = signUpWithLocal(request);

		publishUserSignUpEventSafely(savedUser.getId());

		UserDto.TokenResponse tokens = issueTokens(savedUser, loginContext);

		return UserDto.SignUpResponse.of(UserDto.UserResponse.from(savedUser), tokens);
	}

	@Override
	@Transactional
	public UserDto.SignUpResponse oauthSignUp(UserDto.OAuthSignUpRequest request, LoginContext loginContext) {
		User savedUser = signUpWithOAuth(request);

		publishUserSignUpEventSafely(savedUser.getId());

		UserDto.TokenResponse tokens = issueTokens(savedUser, loginContext);

		return UserDto.SignUpResponse.of(UserDto.UserResponse.from(savedUser), tokens);
	}

	private User signUpWithLocal(UserDto.SignUpRequest request) {
		checkUserAlreadyExist(request.getEmail(), request.getLoginId());

		User user = createUserFromRequest(request);

		return userRepository.save(user);
	}

	private User signUpWithOAuth(UserDto.OAuthSignUpRequest request) {
		if (!temporaryTokenResolver.isTokenValid(request.getTemporaryToken())) {
			throw new DomainException(UserErrorCode.INVALID_TEMPORARY_TOKEN);
		}

		checkEmailAlreadyExist(request.getEmail());

		User user = createUserFromRequest(request);
		return userRepository.save(user);
	}

	private void checkEmailAlreadyExist(String email) {
		if (userRepository.existsByEmail(new Email(email))) {
			throw new DomainException(UserErrorCode.EMAIL_ALREADY_EXISTS);
		}
	}

	private User createUserFromRequest(UserDto.OAuthSignUpRequest request) {
		PhoneNumber phoneNumber = PhoneNumber.parse(request.getPhoneNumber());
		PersonalDetails personalDetails = PersonalDetails.of(
			phoneNumber, request.getBirthDate(),
			request.getName(),
			request.getNickname(),
			new Email(request.getEmail()));

		return User.createSocial(
			temporaryTokenResolver.getOAuthInfoFromTemporaryToken(request.getTemporaryToken()),
			personalDetails,
			passwordEncoder);
	}

	@Override
	@Transactional
	public UserDto.TokenResponse login(UserDto.LoginRequest request, LoginContext loginContext) {
		Timer.Sample sample = Timer.start(meterRegistry);
		try {
			User user = userRepository.findByLoginId(new LoginId(request.getLoginId()))
				.orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

			user.validateLogin(request.getPassword(), passwordEncoder);
			recordLoginAttempt("local", "success");

			UserDto.TokenResponse response = completeLogin(user, loginContext);
			recordLoginDuration("local", "success", sample);
			return response;
		} catch (DomainException ex) {
			recordLoginAttempt("local", "failure");
			recordLoginDuration("local", "failure", sample);
			throw ex;
		}
	}

	@Override
	@Transactional
	public UserDto.OAuthLoginResponse oauthLogin(UserDto.OAuthLoginRequest request, LoginContext loginContext) {
		try {
			OAuthInfo oAuthInfo = OAuthInfo.ofSocial(request.getProvider(), request.getProviderId());

			return userRepository.findByOauthInfo(oAuthInfo)
				.map(user -> handleExistingOAuthUser(user, loginContext))
				.orElseGet(() -> handleNewOAuthUser(request));
		} catch (DomainException ex) {
			recordLoginAttempt("oauth", "failure");
			throw ex;
		}
	}

	private UserDto.OAuthLoginResponse handleExistingOAuthUser(User user, LoginContext loginContext) {
		user.validateActive();
		recordLoginAttempt("oauth", "success");

		return UserDto.OAuthLoginResponse.builder()
			.tokens(completeLogin(user, loginContext))
			.registrationRequired(false)
			.build();
	}

	private UserDto.OAuthLoginResponse handleNewOAuthUser(UserDto.OAuthLoginRequest request) {
		String temporaryToken = temporaryTokenProvider.generateTemporaryToken(
			request.getProvider(),
			request.getProviderId());
		recordLoginAttempt("oauth", "registration_required");

		return UserDto.OAuthLoginResponse.builder()
			.temporaryToken(temporaryToken)
			.registrationRequired(true)
			.build();
	}

	private UserDto.TokenResponse completeLogin(User user, LoginContext loginContext) {
		publishUserSignInEventSafely(user.getId());
		return issueTokens(user, loginContext);
	}

	private void publishUserSignUpEventSafely(UUID userId) {
		try {
			userEventPublisher.publishUserSignUpEvent(userId);
		} catch (Exception ex) {
			log.error("User signup event publish failed, but signup will continue. userId={}", userId, ex);
		}
	}

	private void publishUserSignInEventSafely(UUID userId) {
		try {
			userEventPublisher.publishUserSignInEvent(userId);
		} catch (Exception ex) {
			log.error("User signin event publish failed, but login will continue. userId={}", userId, ex);
		}
	}

	@Override
	@Transactional
	public UserDto.TokenRefreshResponse refreshToken(UserDto.TokenRefreshRequest request) {
		Timer.Sample sample = Timer.start(meterRegistry);
		try {
			AuthTokenClaims claims = tokenResolver.parse(request.getRefreshToken());
			validateRefreshTokenClaims(claims);

			TokenFamily tokenFamily = getRefreshableTokenFamily(claims, request.getRefreshToken());
			validateRefreshTokenOwner(tokenFamily.getUserId());

			UserDto.TokenRefreshResponse response = tokenProvider.refreshToken(
				claims.userId(),
				claims.role(),
				tokenFamily.getId()
			);

			tokenFamily.rotate(hashToken(response.getRefreshToken()), response.getRefreshExpiresAt().toInstant(), Instant.now());
			persistTokenFamily(tokenFamily);

			recordRefreshResult("success");
			recordRefreshDuration("success", sample);
			return response;
		} catch (DomainException ex) {
			recordRefreshResult(ex.getErrorCode().getCode());
			recordRefreshDuration(ex.getErrorCode().getCode(), sample);
			throw ex;
		}
	}

	@Override
	@Transactional
	public void logout(Requester requester) {
		UUID tokenFamilyId = requester.getTokenFamilyId();
		if (tokenFamilyId == null) {
			throw new DomainException(UserErrorCode.MISSING_TOKEN_FAMILY);
		}

		TokenFamily tokenFamily = getOwnedTokenFamily(requester.getUserId(), tokenFamilyId);
		validateRefreshTokenOwner(requester.getUserId());

		tokenFamily.invalidate();
		persistTokenFamily(tokenFamily);
		recordSessionInvalidation("current");
	}

	@Override
	public List<UserDto.SessionResponse> getSessions(Requester requester) {
		validateRefreshTokenOwner(requester.getUserId());
		UUID currentTokenFamilyId = requester.getTokenFamilyId();

		return tokenFamilyRepository.findAvailableByUserId(requester.getUserId()).stream()
			.map(tokenFamily -> toSessionResponse(tokenFamily, currentTokenFamilyId))
			.toList();
	}

	@Override
	@Transactional
	public void logoutSession(UUID userId, UUID tokenFamilyId) {
		TokenFamily tokenFamily = getOwnedTokenFamily(userId, tokenFamilyId);
		validateRefreshTokenOwner(userId);

		tokenFamily.invalidate();
		persistTokenFamily(tokenFamily);
		recordSessionInvalidation("remote");
	}

	private TokenFamily getRefreshableTokenFamily(AuthTokenClaims claims, String refreshToken) {
		if (claims.tokenFamilyId() == null) {
			throw new DomainException(UserErrorCode.INVALID_REFRESH_TOKEN);
		}

		TokenFamily tokenFamily = tokenFamilyRepository.findById(claims.tokenFamilyId())
			.orElseThrow(() -> new DomainException(UserErrorCode.INVALID_REFRESH_TOKEN));

		if (!tokenFamily.isAccessibleBy(claims.userId())) {
			throw new DomainException(UserErrorCode.INVALID_REFRESH_TOKEN);
		}

		if (tokenFamily.getExpiresAt().isBefore(Instant.now())) {
			tokenFamily.markExpired();
			persistTokenFamily(tokenFamily);
			throw new DomainException(UserErrorCode.INVALID_REFRESH_TOKEN);
		}

		if (!tokenFamily.getStatus().isActive()) {
			throw new DomainException(UserErrorCode.INVALID_REFRESH_TOKEN);
		}

		if (!hashToken(refreshToken).equals(tokenFamily.getCurrentRefreshTokenHash())) {
			tokenFamily.markRefreshReuseDetected();
			persistTokenFamily(tokenFamily);
			throw new DomainException(UserErrorCode.REFRESH_TOKEN_REUSED);
		}

		return tokenFamily;
	}

	private TokenFamily getOwnedTokenFamily(UUID userId, UUID tokenFamilyId) {
		TokenFamily tokenFamily = tokenFamilyRepository.findById(tokenFamilyId)
			.orElseThrow(() -> new DomainException(UserErrorCode.TOKEN_FAMILY_NOT_FOUND));
		if (!tokenFamily.isAccessibleBy(userId)) {
			throw new DomainException(UserErrorCode.UNAUTHORIZED_TOKEN_FAMILY_ACCESS);
		}
		return tokenFamily;
	}

	private void validateRefreshTokenClaims(AuthTokenClaims claims) {
		if (claims.tokenType() != AuthTokenType.REFRESH) {
			throw new DomainException(UserErrorCode.INVALID_REFRESH_TOKEN);
		}
	}

	private void validateRefreshTokenOwner(UUID userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));
		user.validateActive();
	}

	private void persistTokenFamily(TokenFamily tokenFamily) {
		TokenFamily savedTokenFamily = tokenFamilyRepository.save(tokenFamily);
		tokenFamilyCacheRepository.save(savedTokenFamily);
	}

	private void checkUserAlreadyExist(String email, String loginId) {
		if (userRepository.existsByEmail(new Email(email))) {
			throw new DomainException(UserErrorCode.EMAIL_ALREADY_EXISTS);
		}

		if (userRepository.existsByLoginId(new LoginId(loginId))) {
			throw new DomainException(UserErrorCode.LOGIN_ID_ALREADY_EXISTS);
		}
	}

	private User createUserFromRequest(UserDto.SignUpRequest request) {
		PersonalDetails personalDetails = PersonalDetails.of(
			PhoneNumber.parse(request.getPhoneNumber()),
			request.getBirthDate(),
			request.getName(),
			request.getNickname(),
			new Email(request.getEmail())
		);
		LoginId loginId = new LoginId(request.getLoginId());
		PasswordHash passwordHash = PasswordHash.create(request.getPassword(), passwordEncoder);

		return User.create(loginId, passwordHash, personalDetails);
	}

	private UserDto.TokenResponse issueTokens(User user, LoginContext loginContext) {
		Instant now = Instant.now();
		TokenFamily tokenFamily = TokenFamily.create(user.getId(), loginContextOrDefault(loginContext), now);
		UserDto.TokenResponse tokenResponse = tokenProvider.generateToken(user.getId(), user.getRole(), tokenFamily.getId());
		tokenFamily.rotate(hashToken(tokenResponse.getRefreshToken()), tokenResponse.getRefreshExpiresAt().toInstant(), now);
		persistTokenFamily(tokenFamily);
		return tokenResponse;
	}

	private LoginContext loginContextOrDefault(LoginContext loginContext) {
		return loginContext == null ? LoginContext.unknown() : loginContext;
	}

	private UserDto.SessionResponse toSessionResponse(TokenFamily tokenFamily, UUID currentTokenFamilyId) {
		return UserDto.SessionResponse.builder()
			.tokenFamilyId(tokenFamily.getId())
			.currentDevice(tokenFamily.getId().equals(currentTokenFamilyId))
			.browserName(tokenFamily.getBrowserName())
			.osName(tokenFamily.getOsName())
			.location(tokenFamily.getLocation())
			.ipAddress(maskIpAddress(tokenFamily.getIpAddress()))
			.lastUsedAt(toOffsetDateTime(tokenFamily.getLastUsedAt()))
			.createdAt(toOffsetDateTime(tokenFamily.getCreatedAt()))
			.status(tokenFamily.getStatus().name())
			.build();
	}

	private OffsetDateTime toOffsetDateTime(Instant instant) {
		if (instant == null) {
			return null;
		}
		return instant.atOffset(ZoneOffset.UTC);
	}

	private OffsetDateTime toOffsetDateTime(LocalDateTime localDateTime) {
		if (localDateTime == null) {
			return null;
		}
		return localDateTime.atOffset(ZoneOffset.UTC);
	}

	private String maskIpAddress(String ipAddress) {
		if (ipAddress == null || ipAddress.isBlank()) {
			return null;
		}
		String[] parts = ipAddress.split("\\.");
		if (parts.length == 4) {
			return parts[0] + "." + parts[1] + ".*.*";
		}
		return ipAddress;
	}

	private String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 algorithm is not available", ex);
		}
	}

	private void recordLoginAttempt(String method, String result) {
		meterRegistry.counter("auth.login.attempts", "method", method, "result", result).increment();
	}

	private void recordLoginDuration(String method, String result, Timer.Sample sample) {
		sample.stop(Timer.builder("auth.login.duration")
			.tag("method", method)
			.tag("result", result)
			.register(meterRegistry));
	}

	private void recordRefreshResult(String result) {
		meterRegistry.counter("auth.refresh.result", "result", result).increment();
	}

	private void recordRefreshDuration(String result, Timer.Sample sample) {
		sample.stop(Timer.builder("auth.refresh.duration")
			.tag("result", result)
			.register(meterRegistry));
	}

	private void recordSessionInvalidation(String scope) {
		meterRegistry.counter("auth.session.invalidate.count", "scope", scope).increment();
	}
}
