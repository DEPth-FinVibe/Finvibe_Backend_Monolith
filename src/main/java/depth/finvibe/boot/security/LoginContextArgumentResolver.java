package depth.finvibe.boot.security;

import depth.finvibe.modules.user.application.port.out.LoginContextResolver;
import depth.finvibe.modules.user.domain.LoginContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class LoginContextArgumentResolver implements HandlerMethodArgumentResolver {

	private final LoginContextResolver loginContextResolver;

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(ResolvedLoginContext.class)
			&& LoginContext.class.isAssignableFrom(parameter.getParameterType());
	}

	@Override
	public @Nullable Object resolveArgument(
		@NonNull MethodParameter parameter,
		@Nullable ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest,
		@Nullable WebDataBinderFactory binderFactory
	) {
		HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
		return loginContextResolver.resolve(request);
	}
}
