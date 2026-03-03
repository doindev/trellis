package io.trellis.repository;

import io.trellis.entity.VariableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface VariableRepository extends JpaRepository<VariableEntity, String> {
    Optional<VariableEntity> findByKey(String key);
    List<VariableEntity> findByProjectId(String projectId);
    List<VariableEntity> findByProjectIdIsNull();
}
