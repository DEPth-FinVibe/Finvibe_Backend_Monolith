package depth.finvibe.modules.study.api.external;

import java.util.List;

import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.study.application.port.in.CourseCommandUseCase;
import depth.finvibe.modules.study.application.port.in.CourseQueryUseCase;
import depth.finvibe.modules.study.dto.CourseDto;

@Tag(name = "학습 코스", description = "코스 생성 및 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/study")
public class StudyCourseController {

    private final CourseCommandUseCase courseCommandUseCase;
    private final CourseQueryUseCase courseQueryUseCase;

    @Operation(summary = "키워드 추천", description = "사용자 관심 종목 기반으로 학습 키워드를 추천합니다")
    @GetMapping("/keywords/recommended")
    public List<String> getRecommendedKeywords(@AuthenticatedUser Requester requester) {
        return courseQueryUseCase.getRecommendedKeywords(requester);
    }

    @Operation(summary = "코스 미리보기", description = "코스 소개 내용을 미리 생성합니다")
    @PostMapping("/courses/preview")
    public CourseDto.ContentPreviewResponse previewCourseContent(
            @RequestBody CourseDto.CreateRequest request,
            @AuthenticatedUser Requester requester
    ) {
        return courseQueryUseCase.previewCourseContent(request, requester);
    }

    @Operation(summary = "코스 생성", description = "코스를 생성하고 레슨 콘텐츠를 구성합니다")
    @PostMapping("/courses")
    public void createCourse(
            @RequestBody CourseDto.CreateRequest request,
            @AuthenticatedUser Requester requester
    ) {
        courseCommandUseCase.createCourse(request, requester);
    }

    @Operation(summary = "내 코스 목록 조회", description = "내 코스와 글로벌 코스를 함께 조회합니다")
    @GetMapping("/courses/me")
    public List<CourseDto.MyCourseResponse> getMyCourses(@AuthenticatedUser Requester requester) {
        return courseQueryUseCase.getMyCourses(requester);
    }
}
