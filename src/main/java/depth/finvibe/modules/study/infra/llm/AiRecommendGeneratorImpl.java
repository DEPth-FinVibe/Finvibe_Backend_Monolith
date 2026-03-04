package depth.finvibe.modules.study.infra.llm;

import depth.finvibe.modules.study.application.port.out.AiRecommendGenerator;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class AiRecommendGeneratorImpl implements AiRecommendGenerator {
    private static final int MAX_RETRY_COUNT = 2;
    private static final String SYSTEM_PROMPT_PATH = "classpath:prompts/study-ai-recommend-generation-system.txt";
    private static final String USER_PROMPT_PATH = "classpath:prompts/study-ai-recommend-generation-user.txt";
    private static final ResponseFormat RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                    .rootElement(JsonObjectSchema.builder()
                            .addStringProperty("content")
                            .required("content")
                            .build())
                    .build())
            .build();
    private static final TypeReference<Map<String, String>> RESPONSE_TYPE = new TypeReference<>() {};

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public AiRecommendGeneratorImpl(
            @Qualifier("chatModel") ChatModel chatModel,
            ObjectMapper objectMapper,
            @Qualifier("webApplicationContext") ResourceLoader resourceLoader
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public String generateStudyRecommendContent(String recentTrades) {
        for (int attempt = 0; attempt <= MAX_RETRY_COUNT; attempt++) {
            try {
                String content = requestRecommendation(recentTrades);
                if (StringUtils.hasText(content)) {
                    return content;
                }
            } catch (IllegalStateException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                if (attempt == MAX_RETRY_COUNT) {
                    break;
                }
            }
        }
        return fallbackRecommendation();
    }

    private String requestRecommendation(String recentTrades) {
        String systemMessage = loadPrompt(SYSTEM_PROMPT_PATH);
        String userMessage = loadPrompt(USER_PROMPT_PATH)
                .replace("{{recent_trades}}", recentTrades == null ? "" : recentTrades);

        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(systemMessage),
                        UserMessage.from(userMessage)
                )
                .responseFormat(RESPONSE_FORMAT)
                .build();

        ChatResponse chatResponse = chatModel.chat(request);
        return parseResponse(chatResponse.aiMessage().text());
    }

    private String parseResponse(String response) {
        try {
            Map<String, String> parsed = objectMapper.readValue(response, RESPONSE_TYPE);
            return parsed.get("content");
        } catch (Exception ex) {
            return null;
        }
    }

    private String loadPrompt(String path) {
        Resource resource = resourceLoader.getResource(path);
        try {
            byte[] bytes = resource.getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load prompt: " + path, ex);
        }
    }

    private String fallbackRecommendation() {
        return "최근 거래 패턴을 기반으로 학습을 추천합니다. "
                + "이번 주에는 분할매수/분할매도 전략과 리스크 관리(손절·익절 기준) 학습을 먼저 진행해보세요.";
    }
}
