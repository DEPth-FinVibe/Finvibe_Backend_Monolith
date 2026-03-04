package depth.finvibe.modules.discussion.application.port.out;

/**
 * 토론 도메인 이벤트를 외부로 전송하기 위한 포트
 */
public interface DiscussionEventPort {
    void publishCreated(Long newsId);

    void publishDeleted(Long newsId);
}
