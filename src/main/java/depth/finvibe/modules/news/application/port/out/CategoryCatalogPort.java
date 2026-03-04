package depth.finvibe.modules.news.application.port.out;

import depth.finvibe.common.insight.domain.CategoryInfo;

import java.util.List;

public interface CategoryCatalogPort {

    List<CategoryInfo> getAll();

    List<CategoryInfo> refresh();
}
