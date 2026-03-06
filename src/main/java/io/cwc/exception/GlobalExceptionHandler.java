package io.cwc.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseException.class)
    public ResponseEntity<Map<String, Object>> handleResponseException(ResponseException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ex.getHttpStatusCode());
        body.put("message", ex.getMessage());
        if (ex.getHint() != null) {
            body.put("hint", ex.getHint());
        }
        if (ex.getMeta() != null) {
            body.put("meta", ex.getMeta());
        }
        return ResponseEntity.status(ex.getHttpStatusCode()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, Object>> issues = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> {
                    Map<String, Object> issue = new LinkedHashMap<>();
                    issue.put("code", "invalid_type");
                    issue.put("message", error.getDefaultMessage());
                    issue.put("path", List.of(error.getField()));
                    return issue;
                })
                .collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 400);
        body.put("message", "Request parameters are not valid");
        body.put("hint", Map.of("issues", issues));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String message = "There is already an entry with this name";
        if (ex.getMessage() != null && ex.getMessage().contains("Unique")) {
            message = "There is already an entry with this name";
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 409);
        body.put("message", message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 500);
        body.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
