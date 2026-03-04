package depth.finvibe.modules.study.application.port.out;

import java.util.List;

public interface UserServiceClient {
    List<String> fetchUserInterestStocks(String userId);
}
