package depth.finvibe.modules.asset.api.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.asset.application.port.in.AssetCommandUseCase;
import depth.finvibe.modules.asset.application.port.in.AssetQueryUseCase;
import depth.finvibe.modules.asset.application.port.in.PortfolioValuationQueryUseCase;
import depth.finvibe.modules.asset.dto.PortfolioValuationDto;

class PortfolioControllerValuationTest {
    @Test
    void returnsDedicatedValuationResponseForAuthenticatedUser() {
        PortfolioValuationQueryUseCase valuationUseCase = mock(PortfolioValuationQueryUseCase.class);
        PortfolioController controller = new PortfolioController(
            mock(AssetCommandUseCase.class),
            mock(AssetQueryUseCase.class),
            valuationUseCase
        );
        var expected = new PortfolioValuationDto.ValuationsResponse(
            PortfolioValuationDto.UserValuationResponse.zero(1L, 0L),
            List.of()
        );
        when(valuationUseCase.getValuations(1L)).thenReturn(expected);

        var response = controller.getValuations(new Requester(1L, null, null));

        assertThat(response.getBody()).isEqualTo(expected);
    }
}
