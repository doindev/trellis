package io.cwc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwaggerSettingsDto {
    private boolean enabled;
    private String apiTitle;
    private String apiDescription;
    private String apiVersion;
}
