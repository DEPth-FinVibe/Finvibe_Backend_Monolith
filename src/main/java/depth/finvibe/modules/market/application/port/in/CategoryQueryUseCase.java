package depth.finvibe.modules.market.application.port.in;

import java.util.List;

import depth.finvibe.modules.market.dto.CategoryDto;
import depth.finvibe.modules.market.dto.CategoryInternalDto;

public interface CategoryQueryUseCase {
    List<CategoryDto.Response> getAllCategories();

    List<CategoryInternalDto.Response> getAllCategoriesForInternal();

    CategoryDto.ChangeRateResponse getCategoryChangeRate(Long categoryId);

    CategoryDto.StockListResponse getCategoryStocksByValue(Long categoryId);
}
