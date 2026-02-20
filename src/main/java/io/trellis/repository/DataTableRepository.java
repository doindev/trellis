package io.trellis.repository;

import io.trellis.entity.DataTableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataTableRepository extends JpaRepository<DataTableEntity, String> {

    List<DataTableEntity> findAllByOrderByCreatedAtDesc();

    Optional<DataTableEntity> findByName(String name);
}
