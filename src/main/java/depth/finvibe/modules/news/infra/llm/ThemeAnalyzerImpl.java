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
import depth.finvibe.modules.news.application.port.out.ThemeAnalyzer;
import depth.finvibe.common.insight.domain.CategoryInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gemini.api-key")
public class ThemeAnalyzerImpl implements ThemeAnalyzer {

    private static final ResponseFormat RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                    .rootElement(JsonObjectSchema.builder()
                            .addStringProperty("analysis")
                            .required("analysis")
                            .build())
                    .build())
            .build();

    private final ChatModel chatModel;
    private final ThemePromptProvider promptProvider;
    private final ObjectMapper objectMapper;

    @Override
    public String analyze(CategoryInfo category, List<String> newsTitles) {
        try {
            String joinedTitles = String.join("\n", newsTitles);
            String systemMessage = promptProvider.getSystemPrompt();
            String userMessage = promptProvider.getUserPrompt(category.name(), joinedTitles);

            ChatRequest request = ChatRequest.builder()
                    .messages(
                            SystemMessage.from(systemMessage),
                            UserMessage.from(userMessage))
                    .responseFormat(RESPONSE_FORMAT)
                    .build();

            ChatResponse response = chatModel.chat(request);
            String responseText = response.aiMessage().text();

            ThemeAnalysisResult result = objectMapper.readValue(responseText, ThemeAnalysisResult.class);
            return result.analysis();
        } catch (Exception ex) {
            log.warn("Failed to analyze theme: {}", ex.getMessage());
            return fallbackAnalysis(category);
        }
    }

    private String fallbackAnalysis(CategoryInfo category) {
        return category.name() + " 이슈 요약";
    }

    private record ThemeAnalysisResult(String analysis) {
    }
}
