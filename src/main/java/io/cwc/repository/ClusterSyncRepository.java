package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.ClusterSyncEntity;

@Repository
public interface ClusterSyncRepository extends JpaRepository<ClusterSyncEntity, String> {
}
