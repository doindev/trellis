package io.trellis.repository;

import io.trellis.entity.DataTableRowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DataTableRowRepository extends JpaRepository<DataTableRowEntity, String> {

    List<DataTableRowEntity> findByDataTableIdOrderByCreatedAtAsc(String dataTableId);

    List<DataTableRowEntity> findByDataTableIdOrderByCreatedAtDesc(String dataTableId);

    void deleteByDataTableId(String dataTableId);

    long countByDataTableId(String dataTableId);
}
