package io.trellis.service;

import io.trellis.entity.DataTableEntity;
import io.trellis.entity.DataTableRowEntity;
import io.trellis.exception.NotFoundException;
import io.trellis.repository.DataTableRepository;
import io.trellis.repository.DataTableRowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataTableService {

    private final DataTableRepository tableRepository;
    private final DataTableRowRepository rowRepository;

    // ---- Table Operations ----

    public List<DataTableEntity> listTables() {
        return tableRepository.findAllByOrderByCreatedAtDesc();
    }

    public DataTableEntity getTable(String id) {
        return tableRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Data table not found: " + id));
    }

    public Optional<DataTableEntity> findTableByName(String name) {
        return tableRepository.findByName(name);
    }

    @Transactional
    public DataTableEntity createTable(String name, List<Map<String, String>> columns) {
        DataTableEntity entity = DataTableEntity.builder()
                .name(name)
                .columnDefinitions(columns)
                .build();
        entity = tableRepository.save(entity);
        log.info("Created data table: {} ({})", name, entity.getId());
        return entity;
    }

    @Transactional
    public void deleteTable(String id) {
        DataTableEntity entity = getTable(id);
        rowRepository.deleteByDataTableId(id);
        tableRepository.delete(entity);
        log.info("Deleted data table: {} ({})", entity.getName(), id);
    }

    // ---- Row Operations ----

    @Transactional
    public DataTableRowEntity insertRow(String tableId, Map<String, Object> data) {
        getTable(tableId); // verify exists
        DataTableRowEntity row = DataTableRowEntity.builder()
                .dataTableId(tableId)
                .rowData(data)
                .build();
        return rowRepository.save(row);
    }

    @Transactional
    public List<DataTableRowEntity> insertRows(String tableId, List<Map<String, Object>> dataList) {
        getTable(tableId); // verify exists
        List<DataTableRowEntity> rows = dataList.stream()
                .map(data -> DataTableRowEntity.builder()
                        .dataTableId(tableId)
                        .rowData(data)
                        .build())
                .toList();
        return rowRepository.saveAll(rows);
    }

    public List<DataTableRowEntity> getRows(String tableId, List<RowFilter> filters,
                                             String matchType, Integer limit, String orderBy,
                                             String orderDirection) {
        getTable(tableId); // verify exists
        List<DataTableRowEntity> rows;
        if ("desc".equalsIgnoreCase(orderDirection)) {
            rows = rowRepository.findByDataTableIdOrderByCreatedAtDesc(tableId);
        } else {
            rows = rowRepository.findByDataTableIdOrderByCreatedAtAsc(tableId);
        }

        // Apply filters
        if (filters != null && !filters.isEmpty()) {
            rows = rows.stream()
                    .filter(row -> matchesFilters(row, filters, matchType))
                    .collect(Collectors.toList());
        }

        // Sort by custom column if specified
        if (orderBy != null && !orderBy.isBlank() && !"createdAt".equals(orderBy)) {
            rows = sortByColumn(rows, orderBy, orderDirection);
        }

        // Apply limit
        if (limit != null && limit > 0 && limit < rows.size()) {
            rows = rows.subList(0, limit);
        }

        return rows;
    }

    @Transactional
    public int deleteRows(String tableId, List<RowFilter> filters, String matchType) {
        List<DataTableRowEntity> allRows = rowRepository.findByDataTableIdOrderByCreatedAtAsc(tableId);
        List<DataTableRowEntity> matching = allRows.stream()
                .filter(row -> matchesFilters(row, filters, matchType))
                .toList();
        rowRepository.deleteAll(matching);
        return matching.size();
    }

    @Transactional
    public List<DataTableRowEntity> updateRows(String tableId, List<RowFilter> filters,
                                                String matchType, Map<String, Object> newData) {
        List<DataTableRowEntity> allRows = rowRepository.findByDataTableIdOrderByCreatedAtAsc(tableId);
        List<DataTableRowEntity> matching = allRows.stream()
                .filter(row -> matchesFilters(row, filters, matchType))
                .toList();

        for (DataTableRowEntity row : matching) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rowData = row.getRowData() instanceof Map
                    ? new LinkedHashMap<>((Map<String, Object>) row.getRowData())
                    : new LinkedHashMap<>();
            rowData.putAll(newData);
            row.setRowData(rowData);
        }

        return rowRepository.saveAll(matching);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public DataTableRowEntity upsertRow(String tableId, String matchColumn,
                                         Map<String, Object> data) {
        Object matchValue = data.get(matchColumn);
        if (matchValue == null) {
            return insertRow(tableId, data);
        }

        List<RowFilter> filters = List.of(new RowFilter(matchColumn, "eq", matchValue));
        List<DataTableRowEntity> allRows = rowRepository.findByDataTableIdOrderByCreatedAtAsc(tableId);
        Optional<DataTableRowEntity> existing = allRows.stream()
                .filter(row -> matchesFilters(row, filters, "allConditions"))
                .findFirst();

        if (existing.isPresent()) {
            DataTableRowEntity row = existing.get();
            Map<String, Object> rowData = row.getRowData() instanceof Map
                    ? new LinkedHashMap<>((Map<String, Object>) row.getRowData())
                    : new LinkedHashMap<>();
            rowData.putAll(data);
            row.setRowData(rowData);
            return rowRepository.save(row);
        } else {
            return insertRow(tableId, data);
        }
    }

    public long countRows(String tableId) {
        return rowRepository.countByDataTableId(tableId);
    }

    // ---- Filter Logic ----

    @SuppressWarnings("unchecked")
    private boolean matchesFilters(DataTableRowEntity row, List<RowFilter> filters, String matchType) {
        if (filters == null || filters.isEmpty()) return true;

        Map<String, Object> data;
        if (row.getRowData() instanceof Map) {
            data = (Map<String, Object>) row.getRowData();
        } else {
            return false;
        }

        if ("anyCondition".equals(matchType)) {
            return filters.stream().anyMatch(f -> matchesCondition(data, f));
        } else {
            return filters.stream().allMatch(f -> matchesCondition(data, f));
        }
    }

    private boolean matchesCondition(Map<String, Object> data, RowFilter filter) {
        Object value = data.get(filter.column());

        return switch (filter.condition()) {
            case "eq" -> objectsEqual(value, filter.value());
            case "neq" -> !objectsEqual(value, filter.value());
            case "contains" -> value != null && String.valueOf(value)
                    .toLowerCase().contains(String.valueOf(filter.value()).toLowerCase());
            case "notContains" -> value == null || !String.valueOf(value)
                    .toLowerCase().contains(String.valueOf(filter.value()).toLowerCase());
            case "isEmpty" -> value == null || String.valueOf(value).isEmpty();
            case "isNotEmpty" -> value != null && !String.valueOf(value).isEmpty();
            case "gt" -> compareNumeric(value, filter.value()) > 0;
            case "gte" -> compareNumeric(value, filter.value()) >= 0;
            case "lt" -> compareNumeric(value, filter.value()) < 0;
            case "lte" -> compareNumeric(value, filter.value()) <= 0;
            case "isTrue" -> Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
            case "isFalse" -> Boolean.FALSE.equals(value) || "false".equalsIgnoreCase(String.valueOf(value));
            default -> false;
        };
    }

    private boolean objectsEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private int compareNumeric(Object a, Object b) {
        try {
            double da = a instanceof Number ? ((Number) a).doubleValue() : Double.parseDouble(String.valueOf(a));
            double db = b instanceof Number ? ((Number) b).doubleValue() : Double.parseDouble(String.valueOf(b));
            return Double.compare(da, db);
        } catch (Exception e) {
            return String.valueOf(a).compareTo(String.valueOf(b));
        }
    }

    @SuppressWarnings("unchecked")
    private List<DataTableRowEntity> sortByColumn(List<DataTableRowEntity> rows, String column,
                                                    String direction) {
        List<DataTableRowEntity> sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> {
            Map<String, Object> dataA = a.getRowData() instanceof Map ? (Map<String, Object>) a.getRowData() : Map.of();
            Map<String, Object> dataB = b.getRowData() instanceof Map ? (Map<String, Object>) b.getRowData() : Map.of();
            Object valA = dataA.get(column);
            Object valB = dataB.get(column);
            int cmp = compareValues(valA, valB);
            return "desc".equalsIgnoreCase(direction) ? -cmp : cmp;
        });
        return sorted;
    }

    private int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        try {
            double da = a instanceof Number ? ((Number) a).doubleValue() : Double.parseDouble(String.valueOf(a));
            double db = b instanceof Number ? ((Number) b).doubleValue() : Double.parseDouble(String.valueOf(b));
            return Double.compare(da, db);
        } catch (Exception e) {
            return String.valueOf(a).compareTo(String.valueOf(b));
        }
    }

    // ---- Filter Record ----

    public record RowFilter(String column, String condition, Object value) {}
}
