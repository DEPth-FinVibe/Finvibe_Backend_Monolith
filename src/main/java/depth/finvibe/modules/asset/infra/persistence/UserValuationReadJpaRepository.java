package depth.finvibe.modules.asset.infra.persistence;

import java.util.Optional;

import org.springframework.data.repository.Repository;

public interface UserValuationReadJpaRepository extends Repository<UserValuationReadEntity, String> {
    Optional<UserValuationReadEntity> findById(String userId);
}
