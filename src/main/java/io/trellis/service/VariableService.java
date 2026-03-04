package io.trellis.service;

import io.trellis.dto.VariableRequest;
import io.trellis.dto.VariableResponse;
import io.trellis.entity.VariableEntity;
import io.trellis.exception.NotFoundException;
import io.trellis.repository.VariableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VariableService {

    private final VariableRepository variableRepository;

    public List<VariableResponse> listVariables() {
        return variableRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<VariableResponse> listVariablesByProject(String projectId) {
        return variableRepository.findByProjectId(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    public VariableResponse getVariable(String id) {
        return toResponse(findById(id));
    }

    @Transactional
    public VariableResponse createVariable(VariableRequest request) {
        VariableEntity entity = VariableEntity.builder()
                .key(request.getKey())
                .value(request.getValue())
                .type(request.getType() != null ? request.getType() : "string")
                .projectId(request.getProjectId())
                .build();
        return toResponse(variableRepository.save(entity));
    }

    @Transactional
    public VariableResponse updateVariable(String id, VariableRequest request) {
        VariableEntity entity = findById(id);
        if (request.getKey() != null) entity.setKey(request.getKey());
        if (request.getValue() != null) entity.setValue(request.getValue());
        if (request.getType() != null) entity.setType(request.getType());
        return toResponse(variableRepository.save(entity));
    }

    @Transactional
    public void deleteVariable(String id) {
        VariableEntity entity = findById(id);
        variableRepository.delete(entity);
    }

    public Map<String, String> getAllVariablesAsMap() {
        return variableRepository.findAll().stream()
                .collect(Collectors.toMap(VariableEntity::getKey, VariableEntity::getValue));
    }

    /**
     * Returns variables for a project: project-specific vars override global vars
     * when keys collide (project vars take precedence).
     */
    public Map<String, String> getVariablesForProject(String projectId) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        // Start with global variables
        for (VariableEntity v : variableRepository.findByProjectIdIsNull()) {
            result.put(v.getKey(), v.getValue());
        }
        // Project-specific variables override globals
        if (projectId != null) {
            for (VariableEntity v : variableRepository.findByProjectId(projectId)) {
                result.put(v.getKey(), v.getValue());
            }
        }
        return result;
    }

    private VariableEntity findById(String id) {
        return variableRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Variable not found: " + id));
    }

    private VariableResponse toResponse(VariableEntity entity) {
        return VariableResponse.builder()
                .id(entity.getId())
                .key(entity.getKey())
                .value(entity.getValue())
                .type(entity.getType())
                .projectId(entity.getProjectId())
                .build();
    }
}
