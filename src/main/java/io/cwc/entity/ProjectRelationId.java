package io.cwc.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectRelationId implements Serializable {
	private static final long serialVersionUID = 1L;
	private String projectId;
    private String userId;
}
