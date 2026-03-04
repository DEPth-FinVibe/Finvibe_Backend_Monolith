package depth.finvibe.common.investment.lock;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 분산 락 관리 인터페이스
 * 다중 노드 환경에서 동시성 제어를 위한 락 메커니즘 제공
 */
public interface DistributedLockManager {

    /**
     * 락을 획득하고 작업을 실행한 후 락을 해제합니다.
     * 락 획득에 실패하면 예외를 던집니다.
     *
     * @param lockKey 락의 고유 키
     * @param waitTime 락 획득 대기 시간
     * @param leaseTime 락 보유 시간 (자동 해제)
     * @param task 실행할 작업
     * @param <T> 반환 타입
     * @return 작업 실행 결과
     * @throws LockAcquisitionException 락 획득에 실패한 경우
     */
    <T> T executeWithLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> task);

    /**
     * 락을 획득하고 작업을 실행한 후 락을 해제합니다. (기본 타임아웃 사용)
     * 대기 시간: 10초, 보유 시간: 30초
     *
     * @param lockKey 락의 고유 키
     * @param task 실행할 작업
     * @param <T> 반환 타입
     * @return 작업 실행 결과
     * @throws LockAcquisitionException 락 획득에 실패한 경우
     */
    <T> T executeWithLock(String lockKey, Supplier<T> task);
}
