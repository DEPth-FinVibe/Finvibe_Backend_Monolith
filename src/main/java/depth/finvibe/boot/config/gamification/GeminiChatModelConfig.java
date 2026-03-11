package depth.finvibe.boot.config.gamification;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties
public class GeminiChatModelConfig {

    @Bean
    @Primary
    @Qualifier("chatModel")
    public ChatModel geminiChatModel(GeminiChatModelProperties properties) {
        GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder = GoogleAiGeminiChatModel.builder()
            .apiKey(properties.apiKey())
            .modelName(properties.modelName());

        if (properties.timeout() != null) {
            builder.timeout(properties.timeout());
        }

        if (properties.temperature() != null) {
            builder.temperature(properties.temperature());
        }

        return builder.build();
    }

    @Bean
    @Qualifier("highModel")
    public ChatModel geminiHighModel(GeminiChatModelProperties properties) {
        GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder = GoogleAiGeminiChatModel.builder()
            .apiKey(properties.apiKey())
            .modelName(properties.highModelName());

        if (properties.timeout() != null) {
            builder.timeout(properties.timeout());
        }

        if (properties.temperature() != null) {
            builder.temperature(properties.temperature());
        }

        return builder.build();
    }
}
