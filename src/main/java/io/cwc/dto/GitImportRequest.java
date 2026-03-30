package io.cwc.dto;

import lombok.Data;

@Data
public class GitImportRequest {

    private String repoUrl;
    private String branch = "main";
    private String token;
    private String provider = "github";
    private String subPath;
    private String mode = "seed";
}
