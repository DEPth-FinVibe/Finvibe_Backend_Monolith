package depth.finvibe.modules.market.application.port.in;

import depth.finvibe.modules.market.domain.Category;
import java.util.List;

public interface CategoryCommandUseCase {
    boolean existsAny();

    void bulkInsert(List<Category> categories);
}
