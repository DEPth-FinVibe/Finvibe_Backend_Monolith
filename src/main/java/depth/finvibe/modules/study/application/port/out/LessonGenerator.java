package depth.finvibe.modules.study.application.port.out;

import depth.finvibe.modules.study.dto.GeneratorDto;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface LessonGenerator {
    List<GeneratorDto.LessonIndex> generateLessonIndex(GeneratorDto.LessonIndexCreateRequest request);

    CompletableFuture<String> generateLessonContent(GeneratorDto.LessonContentCreateContext context);
}
