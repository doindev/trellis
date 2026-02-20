package io.trellis.controller;

import io.trellis.entity.DataTableEntity;
import io.trellis.entity.DataTableRowEntity;
import io.trellis.service.DataTableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/data-tables")
@RequiredArgsConstructor
public class DataTableController {

    private final DataTableService dataTableService;

    @GetMapping
    public List<DataTableEntity> listTables() {
        return dataTableService.listTables();
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public DataTableEntity createTable(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        List<Map<String, String>> columns = (List<Map<String, String>>) body.getOrDefault("columns", List.of());
        return dataTableService.createTable(name, columns);
    }

    @GetMapping("/{id}")
    public DataTableEntity getTable(@PathVariable String id) {
        return dataTableService.getTable(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTable(@PathVariable String id) {
        dataTableService.deleteTable(id);
        return ResponseEntity.ok(Map.of("success", true, "deletedTableId", id));
    }

    @GetMapping("/{id}/rows")
    public List<Map<String, Object>> getRows(@PathVariable String id,
                                              @RequestParam(defaultValue = "50") int limit) {
        List<DataTableRowEntity> rows = dataTableService.getRows(id, null, null, limit, null, "asc");
        return rows.stream().map(this::rowToMap).toList();
    }

    @PostMapping("/{id}/rows")
    @SuppressWarnings("unchecked")
    public Map<String, Object> insertRow(@PathVariable String id, @RequestBody Map<String, Object> body) {
        DataTableRowEntity row = dataTableService.insertRow(id, body);
        return rowToMap(row);
    }

    @GetMapping("/{id}/count")
    public Map<String, Object> countRows(@PathVariable String id) {
        long count = dataTableService.countRows(id);
        return Map.of("tableId", id, "count", count);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rowToMap(DataTableRowEntity row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", row.getId());
        map.put("dataTableId", row.getDataTableId());
        if (row.getRowData() instanceof Map) {
            map.putAll((Map<String, Object>) row.getRowData());
        }
        map.put("createdAt", row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
        map.put("updatedAt", row.getUpdatedAt() != null ? row.getUpdatedAt().toString() : null);
        return map;
    }
}
