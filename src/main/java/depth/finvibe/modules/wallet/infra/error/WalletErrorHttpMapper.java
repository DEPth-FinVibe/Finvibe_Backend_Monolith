package depth.finvibe.modules.wallet.infra.error;

import depth.finvibe.modules.wallet.domain.error.WalletErrorCode;
import depth.finvibe.common.error.DomainErrorCode;
import depth.finvibe.common.infra.error.DomainErrorHttpMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

@Component
public class WalletErrorHttpMapper implements DomainErrorHttpMapper {

  @Override
  public boolean supports(DomainErrorCode code) {
    return code instanceof WalletErrorCode;
  }

  @Override
  public HttpStatusCode toStatus(DomainErrorCode code) {
    WalletErrorCode walletCode = (WalletErrorCode) code;
    return switch (walletCode) {
      case WALLET_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case INVALID_USER_ID, INVALID_MONEY_PRICE, INSUFFICIENT_BALANCE -> HttpStatus.BAD_REQUEST;
    };
  }
}
