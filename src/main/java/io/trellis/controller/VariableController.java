package io.trellis.controller;

import io.trellis.dto.VariableRequest;
import io.trellis.dto.VariableResponse;
import io.trellis.service.VariableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/variables")
@RequiredArgsConstructor
public class VariableController {

    private final VariableService variableService;

    @GetMapping
    public List<VariableResponse> list(@RequestParam(required = false) String projectId) {
        if (projectId != null) {
            return variableService.listVariablesByProject(projectId);
        }
        return variableService.listVariables();
    }

    @GetMapping("/{id}")
    public VariableResponse get(@PathVariable String id) {
        return variableService.getVariable(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VariableResponse create(@RequestBody VariableRequest request) {
        return variableService.createVariable(request);
    }

    @PutMapping("/{id}")
    public VariableResponse update(@PathVariable String id, @RequestBody VariableRequest request) {
        return variableService.updateVariable(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        variableService.deleteVariable(id);
    }
}
