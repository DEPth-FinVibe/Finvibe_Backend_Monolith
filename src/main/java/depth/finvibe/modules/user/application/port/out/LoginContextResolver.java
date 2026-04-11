package depth.finvibe.modules.user.application.port.out;

import depth.finvibe.modules.user.domain.LoginContext;
import jakarta.servlet.http.HttpServletRequest;

public interface LoginContextResolver {
	LoginContext resolve(HttpServletRequest request);
}
