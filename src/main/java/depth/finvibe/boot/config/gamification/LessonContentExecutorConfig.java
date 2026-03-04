package depth.finvibe.boot.config.gamification;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LessonContentExecutorConfig {

    @Bean
    @Qualifier("lessonContentExecutor")
    public Executor lessonContentExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
