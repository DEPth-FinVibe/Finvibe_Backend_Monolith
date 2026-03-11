package depth.finvibe.modules.news.infra.llm;

import tools.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import depth.finvibe.modules.news.application.port.out.NewsAiAnalyzer;
import depth.finvibe.modules.news.domain.EconomicSignal;
import depth.finvibe.modules.news.domain.NewsKeyword;
import depth.finvibe.common.insight.domain.CategoryInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "langchain4j.google-ai-gemini.chat-model.api-key")
public class NewsAiAnalyzerImpl implements NewsAiAnalyzer {

    private static final Long DEFAULT_CATEGORY_ID = 4L;
    private static final int MAX_RETRY_COUNT = 2;
    private static final ResponseFormat RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                    .rootElement(JsonObjectSchema.builder()
                            .addStringProperty("summary")
                            .addStringProperty("signal")
                            .addStringProperty("keyword")
                            .addNumberProperty("categoryId")
                            .required("summary", "signal", "keyword", "categoryId")
                            .build())
                    .build())
            .build();

    private final ChatModel chatModel;
    private final NewsAiPromptProvider promptProvider;
    private final ObjectMapper objectMapper;

    @Override
    public AnalysisResult analyze(String content, List<CategoryInfo> categories) {
        for (int attempt = 0; attempt <= MAX_RETRY_COUNT; attempt++) {
            try {
                return requestAnalysis(content, categories);
            } catch (Exception ex) {
                log.warn("Failed to analyze news (attempt {}): {}", attempt + 1, ex.getMessage());
                if (attempt == MAX_RETRY_COUNT) {
                    break;
                }
            }
        }
        return fallbackResult(categories);
    }

    private AnalysisResult requestAnalysis(String content, List<CategoryInfo> categories) throws Exception {
        String keywordList = Arrays.stream(NewsKeyword.values())
                .map(k -> String.format("%s(%s)", k.name(), k.getLabel()))
                .collect(Collectors.joining(", "));
        String categoryList = categories.stream()
                .map(category -> category.id() + ":" + category.name())
                .collect(Collectors.joining(", "));

        String systemMessage = promptProvider.getSystemPrompt(categoryList, keywordList);
        String userMessage = promptProvider.getUserPrompt(content);

        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(systemMessage),
                        UserMessage.from(userMessage))
                .responseFormat(RESPONSE_FORMAT)
                .build();

        ChatResponse response = chatModel.chat(request);
        String responseText = response.aiMessage().text();

        RawAnalysis raw = objectMapper.readValue(responseText, RawAnalysis.class);
        Long categoryId = resolveCategoryId(raw.categoryId(), categories);
        return new AnalysisResult(
                raw.summary(),
                EconomicSignal.fromString(raw.signal()),
                NewsKeyword.fromString(raw.keyword()),
                categoryId);
    }

    private AnalysisResult fallbackResult(List<CategoryInfo> categories) {
        return new AnalysisResult(
                "뉴스 분석에 실패하여 요약 정보를 제공할 수 없습니다.",
                EconomicSignal.NEUTRAL,
                NewsKeyword.ETF,
                fallbackCategoryId(categories));
    }

    private Long resolveCategoryId(Long categoryId, List<CategoryInfo> categories) {
        if (categoryId == null) {
            return fallbackCategoryId(categories);
        }
        boolean exists = categories.stream().anyMatch(category -> category.id().equals(categoryId));
        return exists ? categoryId : fallbackCategoryId(categories);
    }

    private Long fallbackCategoryId(List<CategoryInfo> categories) {
        return categories.stream()
                .filter(category -> DEFAULT_CATEGORY_ID.equals(category.id()))
                .map(CategoryInfo::id)
                .findFirst()
                .orElse(categories.isEmpty() ? null : categories.get(0).id());
    }

    private record RawAnalysis(String summary, String signal, String keyword, Long categoryId) {
    }
}
