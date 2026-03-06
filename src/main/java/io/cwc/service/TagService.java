package io.cwc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cwc.dto.TagRequest;
import io.cwc.dto.TagResponse;
import io.cwc.entity.TagEntity;
import io.cwc.exception.NotFoundException;
import io.cwc.repository.TagRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    public List<TagResponse> listTags() {
        return tagRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public TagResponse getTag(String id) {
        return toResponse(findById(id));
    }

    @Transactional
    public TagResponse createTag(TagRequest request) {
        TagEntity entity = TagEntity.builder()
                .name(request.getName())
                .build();
        return toResponse(tagRepository.save(entity));
    }

    @Transactional
    public TagResponse updateTag(String id, TagRequest request) {
        TagEntity entity = findById(id);
        entity.setName(request.getName());
        return toResponse(tagRepository.save(entity));
    }

    @Transactional
    public void deleteTag(String id) {
        TagEntity entity = findById(id);
        tagRepository.delete(entity);
    }

    private TagEntity findById(String id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tag not found: " + id));
    }

    private TagResponse toResponse(TagEntity entity) {
        return TagResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
