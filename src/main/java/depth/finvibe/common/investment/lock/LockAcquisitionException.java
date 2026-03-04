package depth.finvibe.common.investment.lock;

/**
 * 분산 락 획득 실패 시 발생하는 예외
 */
public class LockAcquisitionException extends RuntimeException {
    
    public LockAcquisitionException(String lockKey, long waitTimeMillis) {
        super(String.format("Failed to acquire lock for key: %s within %d ms", lockKey, waitTimeMillis));
    }

    public LockAcquisitionException(String lockKey, Throwable cause) {
        super(String.format("Error while acquiring lock for key: %s", lockKey), cause);
    }
}
