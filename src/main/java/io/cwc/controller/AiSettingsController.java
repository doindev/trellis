package io.cwc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import io.cwc.dto.AiSettingsDto;
import io.cwc.service.AiSettingsService;

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
