package depth.finvibe.modules.study.infra.llm;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
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

import depth.finvibe.modules.study.application.port.out.LessonGenerator;
import depth.finvibe.modules.study.dto.GeneratorDto;

@Component
public class LessonGeneratorImpl implements LessonGenerator {
    private static final int MAX_RETRY_COUNT = 2;
    private static final String SYSTEM_PROMPT_PATH = "classpath:prompts/study-lesson-generation-system.txt";
    private static final String INDEX_USER_PROMPT_PATH = "classpath:prompts/study-lesson-index-user.txt";
    private static final String CONTENT_USER_PROMPT_PATH = "classpath:prompts/study-lesson-content-user.txt";
    private static final ResponseFormat INDEX_RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                    .rootElement(JsonArraySchema.builder()
                            .items(JsonObjectSchema.builder()
                                    .addStringProperty("title")
                                    .addStringProperty("description")
                                    .required("title", "description")
                                    .build())
                            .build())
                    .build())
            .build();
    private static final ResponseFormat CONTENT_RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                    .rootElement(JsonObjectSchema.builder()
                            .addStringProperty("content")
                            .required("content")
                            .build())
                    .build())
            .build();
    private static final TypeReference<List<GeneratorDto.LessonIndex>> INDEX_RESPONSE_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> CONTENT_RESPONSE_TYPE = new TypeReference<>() {};

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final Executor lessonContentExecutor;

    public LessonGeneratorImpl(
            @Qualifier("chatModel") ChatModel chatModel,
            ObjectMapper objectMapper,
            @Qualifier("webApplicationContext") ResourceLoader resourceLoader,
            @Qualifier("lessonContentExecutor") Executor lessonContentExecutor
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.lessonContentExecutor = lessonContentExecutor;
    }

    @Override
    public List<GeneratorDto.LessonIndex> generateLessonIndex(GeneratorDto.LessonIndexCreateRequest request) {
        for (int attempt = 0; attempt <= MAX_RETRY_COUNT; attempt++) {
            try {
                List<GeneratorDto.LessonIndex> parsed = requestLessonIndex(request);
                if (isValidIndex(parsed)) {
                    return parsed;
                }
            } catch (IllegalStateException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                if (attempt == MAX_RETRY_COUNT) {
                    break;
                }
            }
        }
        return fallbackIndices(request);
    }

    @Override
    public CompletableFuture<String> generateLessonContent(GeneratorDto.LessonContentCreateContext context) {
        return CompletableFuture.supplyAsync(() -> generateLessonContentSync(context), lessonContentExecutor);
    }

    private String generateLessonContentSync(GeneratorDto.LessonContentCreateContext context) {
        for (int attempt = 0; attempt <= MAX_RETRY_COUNT; attempt++) {
            try {
                String parsed = requestLessonContent(context);
                if (StringUtils.hasText(parsed)) {
                    return parsed;
                }
            } catch (IllegalStateException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                if (attempt == MAX_RETRY_COUNT) {
                    break;
                }
            }
        }
        return fallbackContent(context);
    }

    private List<GeneratorDto.LessonIndex> requestLessonIndex(GeneratorDto.LessonIndexCreateRequest request) {
        String systemMessage = loadPrompt(SYSTEM_PROMPT_PATH);
        String userMessage = loadPrompt(INDEX_USER_PROMPT_PATH)
                .replace("{{course_title}}", request.getCourseTitle())
                .replace("{{keywords}}", String.join(", ", request.getKeywords()));

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(
                        SystemMessage.from(systemMessage),
                        UserMessage.from(userMessage)
                )
                .responseFormat(INDEX_RESPONSE_FORMAT)
                .build();

        ChatResponse chatResponse = chatModel.chat(chatRequest);
        return parseIndices(chatResponse.aiMessage().text());
    }

    private String requestLessonContent(GeneratorDto.LessonContentCreateContext context) {
        String systemMessage = loadPrompt(SYSTEM_PROMPT_PATH);
        String userMessage = loadPrompt(CONTENT_USER_PROMPT_PATH)
                .replace("{{course_title}}", context.getCourseTitle())
                .replace("{{keywords}}", String.join(", ", context.getKeywords()))
                .replace("{{lesson_title}}", context.getLessonTitle())
                .replace("{{lesson_description}}", context.getLessonDescription());

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(
                        SystemMessage.from(systemMessage),
                        UserMessage.from(userMessage)
                )
                .responseFormat(CONTENT_RESPONSE_FORMAT)
                .build();

        ChatResponse chatResponse = chatModel.chat(chatRequest);
        return parseContent(chatResponse.aiMessage().text());
    }

    private List<GeneratorDto.LessonIndex> parseIndices(String response) {
        try {
            return objectMapper.readValue(response, INDEX_RESPONSE_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String parseContent(String response) {
        try {
            Map<String, String> parsed = objectMapper.readValue(response, CONTENT_RESPONSE_TYPE);
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

    private boolean isValidIndex(List<GeneratorDto.LessonIndex> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }

        return items.stream().allMatch(this::isValidIndexItem);
    }

    private boolean isValidIndexItem(GeneratorDto.LessonIndex item) {
        if (item == null) {
            return false;
        }

        if (!StringUtils.hasText(item.getTitle())) {
            return false;
        }

        return StringUtils.hasText(item.getDescription());
    }

    private List<GeneratorDto.LessonIndex> fallbackIndices(GeneratorDto.LessonIndexCreateRequest request) {
        return List.of(
                GeneratorDto.LessonIndex.of("Introduction", "Overview of the topic and learning goals."),
                GeneratorDto.LessonIndex.of("Core Concepts", "Key ideas and terminology to understand."),
                GeneratorDto.LessonIndex.of("Practical Examples", "Applying the concepts to real cases.")
        );
    }

    private String fallbackContent(GeneratorDto.LessonContentCreateContext context) {
        return "Lesson: " + context.getLessonTitle() + "\n"
                + context.getLessonDescription() + "\n"
                + "Key points: " + String.join(", ", context.getKeywords()) + ".";
    }
}
