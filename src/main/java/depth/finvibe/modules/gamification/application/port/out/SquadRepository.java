package depth.finvibe.modules.gamification.application.port.out;

import depth.finvibe.modules.gamification.domain.Squad;

import java.util.List;
import java.util.Optional;

public interface SquadRepository {
    void save(Squad squad);
    void delete(Squad squad);
    List<Squad> findAll();
    Optional<Squad> findById(Long id);
}
