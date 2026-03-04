package depth.finvibe.modules.asset.infra.error;

import depth.finvibe.modules.asset.domain.error.AssetErrorCode;
import depth.finvibe.common.investment.error.DomainErrorCode;
import depth.finvibe.common.investment.infra.error.DomainErrorHttpMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

@Component
public class AssetErrorHttpMapper implements DomainErrorHttpMapper {

    @Override
    public boolean supports(DomainErrorCode code) {
        return code instanceof AssetErrorCode;
    }

    @Override
    public HttpStatusCode toStatus(DomainErrorCode code) {
        AssetErrorCode assetCode = (AssetErrorCode) code;
        return switch (assetCode) {
            case CANNOT_SELL_NON_EXISTENT_ASSET,
                 INVALID_PORTFOLIO_GROUP_PARAMS,
                 INVALID_PORTFOLIO_CHART_DATE_RANGE,
                 SAME_PORTFOLIO_GROUP_TRANSFER_NOT_ALLOWED,
                 CANNOT_MODIFY_DEFAULT_PORTFOLIO_GROUP,
                 CANNOT_DELETE_DEFAULT_PORTFOLIO_GROUP,
                 NEGATIVE_MONEY_AMOUNT,
                 INVALID_MONEY_PARAMS,
                 CANNOT_ADD_DIFFERENT_CURRENCIES,
                 CANNOT_SUBTRACT_DIFFERENT_CURRENCIES -> HttpStatus.BAD_REQUEST;

            case ONLY_OWNER_CAN_UNREGISTER_ASSET,
                 ONLY_OWNER_CAN_REGISTER_ASSET,
                 ONLY_OWNER_CAN_TRANSFER_ASSET,
                 ONLY_OWNER_CAN_VIEW_ASSETS,
                 ONLY_OWNER_CAN_DELETE_PORTFOLIO_GROUP -> HttpStatus.FORBIDDEN;

            case ASSET_NOT_FOUND,
                 PORTFOLIO_GROUP_NOT_FOUND,
                 DEFAULT_PORTFOLIO_GROUP_NOT_FOUND -> HttpStatus.NOT_FOUND;

            case DEFAULT_PORTFOLIO_GROUP_ALREADY_EXISTS -> HttpStatus.CONFLICT;
        };
    }
}
