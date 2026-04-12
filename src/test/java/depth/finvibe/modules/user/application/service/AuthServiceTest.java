package depth.finvibe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import depth.finvibe.boot.security.Requester;
import depth.finvibe.common.error.DomainException;
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
import depth.finvibe.modules.user.domain.enums.TokenFamilyStatus;
import depth.finvibe.modules.user.domain.enums.UserRole;
import depth.finvibe.modules.user.domain.error.UserErrorCode;
import depth.finvibe.modules.user.domain.vo.Email;
import depth.finvibe.modules.user.domain.vo.LoginId;
import depth.finvibe.modules.user.domain.vo.PasswordHash;
import depth.finvibe.modules.user.domain.vo.PersonalDetails;
import depth.finvibe.modules.user.domain.vo.PhoneNumber;
import depth.finvibe.modules.user.dto.UserDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserEventPublisher userEventPublisher;

	@Mock
	private TokenProvider tokenProvider;

	@Mock
	private TokenResolver tokenResolver;

	@Mock
	private TokenFamilyRepository tokenFamilyRepository;

	@Mock
	private TokenFamilyCacheRepository tokenFamilyCacheRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private TemporaryTokenProvider temporaryTokenProvider;

	@Mock
	private TemporaryTokenResolver temporaryTokenResolver;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(
			userRepository,
			userEventPublisher,
			tokenProvider,
			tokenResolver,
			tokenFamilyRepository,
			tokenFamilyCacheRepository,
			passwordEncoder,
			temporaryTokenProvider,
			temporaryTokenResolver,
			new SimpleMeterRegistry()
		);
	}

	@Test
	void loginCreatesTokenFamilyAndCachesIt() {
		User user = createUser("user01", "password123");
		LoginContext loginContext = new LoginContext("127.0.0.1", null, "Chrome", "macOS", "Mozilla/5.0");

		when(userRepository.findByLoginId(any(LoginId.class))).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("password123", "encoded-password123")).thenReturn(true);
		when(tokenProvider.generateToken(eq(user.getId()), eq(user.getRole()), any(UUID.class)))
			.thenAnswer(invocation -> tokenResponse((UUID) invocation.getArgument(2), "refresh-token"));
		when(tokenFamilyRepository.save(any(TokenFamily.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserDto.TokenResponse response = authService.login(
			UserDto.LoginRequest.builder()
				.loginId("user01")
				.password("password123")
				.build(),
			loginContext
		);

		ArgumentCaptor<TokenFamily> familyCaptor = ArgumentCaptor.forClass(TokenFamily.class);
		verify(tokenFamilyRepository).save(familyCaptor.capture());
		verify(tokenFamilyCacheRepository).save(any(TokenFamily.class));

		TokenFamily savedFamily = familyCaptor.getValue();
		assertThat(response.getTokenFamilyId()).isEqualTo(savedFamily.getId());
		assertThat(savedFamily.getUserId()).isEqualTo(user.getId());
		assertThat(savedFamily.getStatus()).isEqualTo(TokenFamilyStatus.ACTIVE);
		assertThat(savedFamily.getCurrentRefreshTokenHash()).isEqualTo(hash("refresh-token"));
		assertThat(savedFamily.getBrowserName()).isEqualTo("Chrome");
		assertThat(savedFamily.getOsName()).isEqualTo("macOS");
	}

	@Test
	void refreshRotatesRefreshTokenWhenFamilyMatches() {
		User user = createUser("user01", "password123");
		TokenFamily tokenFamily = TokenFamily.create(user.getId(), LoginContext.unknown(), Instant.now());
		tokenFamily.rotate(hash("old-refresh-token"), Instant.now().plusSeconds(3600), Instant.now());

		when(tokenResolver.parse("old-refresh-token")).thenReturn(new AuthTokenClaims(
			user.getId(),
			user.getRole(),
			tokenFamily.getId(),
			AuthTokenType.REFRESH,
			UUID.randomUUID(),
			Instant.now().plusSeconds(3600)
		));
		when(tokenFamilyRepository.findById(tokenFamily.getId())).thenReturn(Optional.of(tokenFamily));
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(tokenProvider.refreshToken(user.getId(), user.getRole(), tokenFamily.getId()))
			.thenReturn(tokenRefreshResponse(tokenFamily.getId(), "new-refresh-token"));
		when(tokenFamilyRepository.save(any(TokenFamily.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserDto.TokenRefreshResponse response = authService.refreshToken(
			UserDto.TokenRefreshRequest.builder()
				.refreshToken("old-refresh-token")
				.build()
		);

		assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
		assertThat(tokenFamily.getCurrentRefreshTokenHash()).isEqualTo(hash("new-refresh-token"));
		assertThat(tokenFamily.getStatus()).isEqualTo(TokenFamilyStatus.ACTIVE);
		verify(tokenFamilyCacheRepository).save(tokenFamily);
	}

	@Test
	void refreshReuseInvalidatesTokenFamily() {
		User user = createUser("user01", "password123");
		TokenFamily tokenFamily = TokenFamily.create(user.getId(), LoginContext.unknown(), Instant.now());
		tokenFamily.rotate(hash("current-refresh-token"), Instant.now().plusSeconds(3600), Instant.now());

		when(tokenResolver.parse("reused-refresh-token")).thenReturn(new AuthTokenClaims(
			user.getId(),
			user.getRole(),
			tokenFamily.getId(),
			AuthTokenType.REFRESH,
			UUID.randomUUID(),
			Instant.now().plusSeconds(3600)
		));
		when(tokenFamilyRepository.findById(tokenFamily.getId())).thenReturn(Optional.of(tokenFamily));
		when(tokenFamilyRepository.save(any(TokenFamily.class))).thenAnswer(invocation -> invocation.getArgument(0));

		assertThatThrownBy(() -> authService.refreshToken(
			UserDto.TokenRefreshRequest.builder()
				.refreshToken("reused-refresh-token")
				.build()
		))
			.isInstanceOf(DomainException.class)
			.extracting(ex -> ((DomainException) ex).getErrorCode())
			.isEqualTo(UserErrorCode.REFRESH_TOKEN_REUSED);

		assertThat(tokenFamily.getStatus()).isEqualTo(TokenFamilyStatus.REUSED_DETECTED);
		verify(tokenFamilyCacheRepository).save(tokenFamily);
	}

	@Test
	void logoutInvalidatesCurrentTokenFamily() {
		User user = createUser("user01", "password123");
		TokenFamily tokenFamily = TokenFamily.create(user.getId(), LoginContext.unknown(), Instant.now());
		tokenFamily.rotate(hash("refresh-token"), Instant.now().plusSeconds(3600), Instant.now());

		when(tokenFamilyRepository.findById(tokenFamily.getId())).thenReturn(Optional.of(tokenFamily));
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(tokenFamilyRepository.save(any(TokenFamily.class))).thenAnswer(invocation -> invocation.getArgument(0));

		authService.logout(new Requester(user.getId(), user.getRole(), tokenFamily.getId()));

		assertThat(tokenFamily.getStatus()).isEqualTo(TokenFamilyStatus.INVALIDATED);
		verify(tokenFamilyCacheRepository).save(tokenFamily);
	}

	@Test
	void getSessionsReturnsMaskedIpAndCurrentDeviceFlag() {
		User user = createUser("user01", "password123");
		TokenFamily currentFamily = TokenFamily.create(
			user.getId(),
			new LoginContext("127.0.0.1", null, "Chrome", "macOS", "Mozilla/5.0"),
			Instant.now()
		);
		currentFamily.rotate(hash("refresh-token"), Instant.now().plusSeconds(3600), Instant.now());

		TokenFamily anotherFamily = TokenFamily.create(
			user.getId(),
			new LoginContext("10.10.10.10", null, "Safari", "iOS", "Mozilla/5.0"),
			Instant.now()
		);
		anotherFamily.rotate(hash("refresh-token-2"), Instant.now().plusSeconds(3600), Instant.now());

		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(tokenFamilyRepository.findAvailableByUserId(user.getId())).thenReturn(List.of(currentFamily, anotherFamily));

		List<UserDto.SessionResponse> responses = authService.getSessions(
			new Requester(user.getId(), user.getRole(), currentFamily.getId())
		);

		assertThat(responses).hasSize(2);
		assertThat(responses.get(0).isCurrentDevice()).isTrue();
		assertThat(responses.get(0).getIpAddress()).isEqualTo("127.0.*.*");
		assertThat(responses.get(1).isCurrentDevice()).isFalse();
		assertThat(responses.get(1).getIpAddress()).isEqualTo("10.10.*.*");
	}

	private User createUser(String loginId, String password) {
		when(passwordEncoder.encode(password)).thenReturn("encoded-" + password);
		PasswordHash passwordHash = PasswordHash.create(password, passwordEncoder);
		return User.create(
			new LoginId(loginId),
			passwordHash,
			PersonalDetails.of(
				PhoneNumber.parse("010-1234-5678"),
				LocalDate.of(1990, 1, 1),
				"홍길동",
				"핀바이브",
				new Email("user@example.com")
			)
		);
	}

	private UserDto.TokenResponse tokenResponse(UUID tokenFamilyId, String refreshToken) {
		return UserDto.TokenResponse.builder()
			.accessToken("access-token")
			.accessExpiresAt(OffsetDateTime.now().plusHours(1))
			.refreshToken(refreshToken)
			.refreshExpiresAt(OffsetDateTime.now().plusDays(30))
			.tokenFamilyId(tokenFamilyId)
			.build();
	}

	private UserDto.TokenRefreshResponse tokenRefreshResponse(UUID tokenFamilyId, String refreshToken) {
		return UserDto.TokenRefreshResponse.builder()
			.accessToken("new-access-token")
			.accessExpiresAt(OffsetDateTime.now().plusHours(1))
			.refreshToken(refreshToken)
			.refreshExpiresAt(OffsetDateTime.now().plusDays(30))
			.tokenFamilyId(tokenFamilyId)
			.build();
	}

	private String hash(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
