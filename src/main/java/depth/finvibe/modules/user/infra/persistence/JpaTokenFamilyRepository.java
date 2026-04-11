package depth.finvibe.modules.user.infra.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.user.domain.TokenFamily;

public interface JpaTokenFamilyRepository extends JpaRepository<TokenFamily, UUID> {
	List<TokenFamily> findAllByUserIdOrderByLastUsedAtDescCreatedAtDesc(UUID userId);
}
