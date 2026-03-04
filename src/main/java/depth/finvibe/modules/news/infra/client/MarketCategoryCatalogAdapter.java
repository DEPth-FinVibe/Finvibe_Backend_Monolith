package depth.finvibe.modules.news.infra.client;

import depth.finvibe.modules.news.application.port.out.CategoryCatalogPort;
import depth.finvibe.modules.news.dto.MarketCategoryResponse;
import depth.finvibe.common.insight.domain.CategoryInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class MarketCategoryCatalogAdapter implements CategoryCatalogPort {

    public static final String CACHE_NAME = "marketCategories";
    private static final String CACHE_KEY = "all";

    private final HttpMarketCategoryClient httpMarketCategoryClient;
    private final CacheManager cacheManager;

    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'" + CACHE_KEY + "'")
    public List<CategoryInfo> getAll() {
        return fetchCategories();
    }

    @Override
    public List<CategoryInfo> refresh() {
        List<CategoryInfo> latest = fetchCategories();
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.put(CACHE_KEY, latest);
        }
        return latest;
    }

    private List<CategoryInfo> fetchCategories() {
        return Objects.requireNonNull(httpMarketCategoryClient.getCategories(), "category response must not be null")
                .stream()
                .map(this::toCategoryInfo)
                .toList();
    }

    private CategoryInfo toCategoryInfo(MarketCategoryResponse response) {
        return new CategoryInfo(response.categoryId(), response.categoryName());
    }
}
