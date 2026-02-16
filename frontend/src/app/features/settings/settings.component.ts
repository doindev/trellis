import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent {
  settings = {
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    executionTimeout: 300,
    saveExecutionProgress: true,
    saveManualExecutions: true,
    defaultExecutionMode: 'MANUAL'
  };

  saveSettings(): void {
    console.log('Saving settings:', this.settings);
    // TODO: Implement settings save API
  }
}
