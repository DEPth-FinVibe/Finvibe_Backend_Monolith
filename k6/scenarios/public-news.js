import { getJson, randomThinkTime } from '../lib/http.js';
import { pickFrom, sharedRuntimeData } from '../lib/data.js';

export function runPublicNewsFlow() {
	const newsId = pickFrom(sharedRuntimeData.newsIds);
	const categoryId = pickFrom(sharedRuntimeData.categoryIds);
	const page = Math.floor(Math.random() * 3); // 0~2 페이지 다양하게 분산

	getJson('/news', {
		query: {
			page,
			size: 20,
			sortType: Math.random() < 0.7 ? 'LATEST' : 'POPULAR',
		},
		tags: { module: 'news', flow: 'news_feed', scope: 'public' },
	});
	randomThinkTime(0.5, 1.5);

	getJson(`/news/${newsId}`, {
		tags: { module: 'news', flow: 'news_detail', scope: 'public' },
	});
	randomThinkTime(0.5, 1.2);

	getJson('/news/keywords/trending', {
		tags: { module: 'news', flow: 'trending_keywords', scope: 'public' },
	});
	randomThinkTime(0.5, 1.2);

	getJson('/themes/today', {
		tags: { module: 'theme', flow: 'daily_theme', scope: 'public' },
	});
	randomThinkTime(0.5, 1.2);

	getJson(`/themes/today/${categoryId}`, {
		tags: { module: 'theme', flow: 'daily_theme_detail', scope: 'public' },
		expectedStatuses: [200, 404],
	});
}
