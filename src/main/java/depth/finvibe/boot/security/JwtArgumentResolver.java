package depth.finvibe.boot.security;

import depth.finvibe.modules.user.application.port.out.TokenResolver;
import depth.finvibe.modules.user.domain.AuthTokenClaims;
import depth.finvibe.modules.user.domain.enums.AuthTokenType;
import depth.finvibe.modules.user.domain.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class JwtArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHENTICATED_USER_ID_HEADER = "X-Authenticated-User-Id";
    private static final String AUTHENTICATED_ROLE_HEADER = "X-Authenticated-Role";
    private static final String TOKEN_FAMILY_ID_HEADER = "X-Token-Family-Id";

    private final TokenResolver tokenResolver;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedUser.class)
                && Requester.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public @Nullable Object resolveArgument(
            @NonNull MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory
    ) throws Exception {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        Requester requesterFromHeaders = resolveFromTrustedHeaders(request);
        if (requesterFromHeaders != null) {
            return requesterFromHeaders;
        }

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        AuthTokenClaims claims;
        try {
            claims = tokenResolver.parse(token);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (claims.tokenType() != AuthTokenType.ACCESS) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return new Requester(
                claims.userId(),
                claims.role(),
                claims.tokenFamilyId()
        );
    }

    private @Nullable Requester resolveFromTrustedHeaders(@Nullable HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String userIdHeader = request.getHeader(AUTHENTICATED_USER_ID_HEADER);
        String roleHeader = request.getHeader(AUTHENTICATED_ROLE_HEADER);
        if (userIdHeader == null || roleHeader == null) {
            return null;
        }

        UUID tokenFamilyId = null;
        String tokenFamilyHeader = request.getHeader(TOKEN_FAMILY_ID_HEADER);
        if (tokenFamilyHeader != null && !tokenFamilyHeader.isBlank()) {
            tokenFamilyId = UUID.fromString(tokenFamilyHeader);
        }

        return new Requester(
            UUID.fromString(userIdHeader),
            UserRole.valueOf(roleHeader),
            tokenFamilyId
        );
    }
}
