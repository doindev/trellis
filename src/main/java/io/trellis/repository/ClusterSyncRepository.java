package io.trellis.repository;

import io.trellis.entity.ClusterSyncEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClusterSyncRepository extends JpaRepository<ClusterSyncEntity, String> {
}
