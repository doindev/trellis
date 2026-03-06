package io.cwc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.cwc.dto.TagRequest;
import io.cwc.dto.TagResponse;
import io.cwc.service.TagService;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public List<TagResponse> list() {
        return tagService.listTags();
    }

    @GetMapping("/{id}")
    public TagResponse get(@PathVariable String id) {
        return tagService.getTag(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse create(@RequestBody TagRequest request) {
        return tagService.createTag(request);
    }

    @PutMapping("/{id}")
    public TagResponse update(@PathVariable String id, @RequestBody TagRequest request) {
        return tagService.updateTag(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        tagService.deleteTag(id);
    }
}
