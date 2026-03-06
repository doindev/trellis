package io.cwc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.cwc.entity.TagEntity;

import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<TagEntity, String> {
    Optional<TagEntity> findByName(String name);
}
