package io.trellis.controller;

import io.trellis.dto.AiSettingsDto;
import io.trellis.service.AiSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/ai")
@RequiredArgsConstructor
public class AiSettingsController {

    private final AiSettingsService aiSettingsService;

    @GetMapping
    public AiSettingsDto getSettings() {
        return aiSettingsService.getSettings();
    }

    @PutMapping
    public AiSettingsDto updateSettings(@RequestBody AiSettingsDto dto) {
        return aiSettingsService.saveSettings(dto);
    }
}
