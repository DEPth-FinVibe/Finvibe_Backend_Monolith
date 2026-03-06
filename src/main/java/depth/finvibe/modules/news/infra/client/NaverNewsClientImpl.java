package depth.finvibe.modules.news.infra.client;

import depth.finvibe.modules.news.application.port.out.NewsCrawler;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class NaverNewsClientImpl implements NewsCrawler {

    private static final String NAVER_FINANCE_NEWS_URL = "https://finance.naver.com/news/mainnews.naver";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Pattern PUBLISHED_AT_PATTERN = Pattern.compile("(\\d{4}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2})");
    private static final DateTimeFormatter PUBLISHED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final Pattern REDIRECT_PATTERN = Pattern.compile("top\\.location\\.href\\s*=\\s*['\"](.*?)['\"]");

    @Value("${news.crawler.max-items:30}")
    private int maxItems;

    @Override
    public List<RawNewsData> fetchLatestRawNews() {
        List<RawNewsData> newsList = new ArrayList<>();
        int candidateCount = 0;
        int missingContentCount = 0;
        try {
            log.info("Fetching latest news from Naver Finance...");
            Document doc = Jsoup.connect(NAVER_FINANCE_NEWS_URL)
                    .userAgent(USER_AGENT)
                    .get();

            Elements newsElements = selectNewsElements(doc);
            if (newsElements.isEmpty()) {
                log.warn("No news elements found. title={}, linkCount={}",
                        doc.title(),
                        doc.select("a[href]").size());
            }

            for (Element element : newsElements) {
                Element linkElement = findLinkElement(element);
                if (linkElement == null) {
                    continue;
                }

                String title = linkElement.text();
                String detailUrl = buildDetailUrl(linkElement.attr("href"));
                if (detailUrl == null) {
                    continue;
                }
                candidateCount++;

                // 상세 페이지 접속하여 본문/발행시간/신문사 가져오기
                NewsDetail detail = fetchDetail(detailUrl);

                if (detail.contentHtml() != null && !detail.contentHtml().isEmpty()
                        && detail.contentText() != null && !detail.contentText().isEmpty()) {
                    newsList.add(new RawNewsData(
                            title,
                            detail.contentHtml(),
                            detail.contentText(),
                            detail.publishedAt(),
                            detail.provider()));
                } else {
                    missingContentCount++;
                    log.warn("Empty content for url={}", detailUrl);
                }

                if (newsList.size() >= maxItems)
                    break;
            }

            log.info("Successfully fetched {} news items.", newsList.size());
            if (newsList.isEmpty()) {
                log.warn("No news items saved. candidates={}, missingContent={}",
                        candidateCount,
                        missingContentCount);
            }
        } catch (IOException e) {
            log.error("Failed to fetch news from Naver: {}", e.getMessage());
        }
        return newsList;
    }

    private Elements selectNewsElements(Document doc) {
        List<String> selectors = List.of(
                ".mainNewsList .block1",
                ".mainNewsList li",
                "ul.newsList li",
                ".newsList li",
                "ul.news_list li",
                ".main_news li");

        for (String selector : selectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                return elements;
            }
        }
        return new Elements();
    }

    private Element findLinkElement(Element element) {
        Elements linkCandidates = element.select("a[href]");
        for (Element candidate : linkCandidates) {
            if (!candidate.text().isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private String buildDetailUrl(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        if (href.startsWith("http")) {
            return href;
        }
        if (href.startsWith("/")) {
            return "https://finance.naver.com" + href;
        }
        return "https://finance.naver.com/" + href;
    }

    private NewsDetail fetchDetail(String url) {
        return fetchDetail(url, 0);
    }

    private NewsDetail fetchDetail(String url, int depth) {
        if (depth > 3) {
            log.warn("Max redirect depth reached for url={}", url);
            return new NewsDetail(null, null, null, null);
        }

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .get();

            // Check for JavaScript redirect
            String redirectUrl = extractScriptRedirect(doc);
            if (redirectUrl != null) {
                log.info("Following JS redirect: {} -> {}", url, redirectUrl);
                return fetchDetail(redirectUrl, depth + 1);
            }

            Element contentElement = selectContentElement(doc);
            if (contentElement != null) {
                LocalDateTime publishedAt = extractPublishedAt(doc);
                String provider = extractProvider(doc);

                Element cleanedContent = sanitizeContent(contentElement);
                String html = cleanedContent.html().trim();
                String text = cleanedContent.text().trim();
                if (html.isBlank() || text.isBlank()) {
                    log.warn("Content element found but empty. title={}", doc.title());
                    return new NewsDetail(null, null, publishedAt, provider);
                }
                return new NewsDetail(html, text, publishedAt, provider);
            }
        } catch (IOException e) {
            log.warn("Failed to fetch content from {}: {}", url, e.getMessage());
        }
        return new NewsDetail(null, null, null, null);
    }

    private Element sanitizeContent(Element contentElement) {
        Element clone = contentElement.clone();
        clone.select(".link_news").remove();
        clone.select(".date").remove();
        clone.select("script, style").remove();
        return clone;
    }

    private String extractScriptRedirect(Document doc) {
        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String data = script.data();
            if (data.contains("top.location.href")) {
                Matcher matcher = REDIRECT_PATTERN.matcher(data);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        return null;
    }

    private Element selectContentElement(Document doc) {
        List<String> selectors = List.of(
                "#dic_area",
                "article#dic_area",
                "article._article_content",
                "#news_read",
                "#news_read_body_id",
                "#news_read .articleCont",
                "#newsct",
                "#newsct_article",
                "#newsEndContents",
                ".article_body",
                ".articleCont",
                "article",
                "#content");

        for (String selector : selectors) {
            Element element = doc.selectFirst(selector);
            if (element != null && !element.text().isBlank()) {
                return element;
            }
        }
        for (String selector : selectors) {
            Element element = doc.selectFirst(selector);
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    private LocalDateTime extractPublishedAt(Document doc) {
        List<String> selectors = List.of(
                ".article_info .date",
                ".article_info .article_time",
                ".media_end_head_info_datestamp_time",
                ".date");

        for (String selector : selectors) {
            Element element = doc.selectFirst(selector);
            if (element == null) {
                continue;
            }
            LocalDateTime parsed = parsePublishedAt(element.text());
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private LocalDateTime parsePublishedAt(String text) {
        Matcher matcher = PUBLISHED_AT_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return LocalDateTime.parse(matcher.group(1), PUBLISHED_AT_FORMATTER);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private String extractProvider(Document doc) {
        List<String> selectors = List.of(
                ".article_info .press",
                ".article_info .company",
                ".article_sponsor",
                ".media_end_head_top_logo img",
                ".source");

        for (String selector : selectors) {
            Element element = doc.selectFirst(selector);
            if (element == null) {
                continue;
            }
            String text = element.hasAttr("alt") ? element.attr("alt") : element.text();
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    private record NewsDetail(String contentHtml, String contentText, LocalDateTime publishedAt, String provider) {
    }
}
