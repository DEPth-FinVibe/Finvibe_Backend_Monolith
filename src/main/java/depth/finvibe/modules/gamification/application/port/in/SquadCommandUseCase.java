package depth.finvibe.modules.gamification.application.port.in;

import depth.finvibe.boot.security.Requester;

public interface SquadCommandUseCase {
    /**
     * 사용자를 특정 스쿼드에 참여시킵니다.
     *
     * @param squadId 스쿼드 ID
     * @param requester 요청자 정보
     */
    void joinSquad(Long squadId, Requester requester);

    /**
     * 새로운 스쿼드를 생성합니다.
     *
     * @param name 스쿼드 이름
     * @param region 지역
     * @param requester 요청자 정보
     * @return 생성된 스쿼드 ID
     */
    Long createSquad(String name, String region, Requester requester);

    /**
     * 스쿼드 정보를 수정합니다.
     *
     * @param squadId 스쿼드 ID
     * @param name 스쿼드 이름
     * @param region 지역
     * @param requester 요청자 정보
     */
    void updateSquad(Long squadId, String name, String region, Requester requester);

    /**
     * 스쿼드를 삭제합니다.
     *
     * @param squadId 스쿼드 ID
     * @param requester 요청자 정보
     */
    void deleteSquad(Long squadId, Requester requester);
}
