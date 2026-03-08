import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';

@Component({
    selector: 'app-timezone-param',
    imports: [CommonModule, FormsModule],
    template: `
    <div class="param-header">
      <label class="param-label">
        {{ param.displayName }}
        @if (param.required) { <span class="required">*</span> }
      </label>
    </div>
    @if (param.description) {
      <p class="param-description">{{ param.description }}</p>
    }
    <div class="tz-wrapper" #wrapper>
      <input type="text"
             class="form-control param-input"
             [ngModel]="displayValue"
             (ngModelChange)="onInput($event)"
             (focus)="onFocus()"
             (blur)="onBlur()"
             [placeholder]="param.placeHolder || 'UTC'"
             [disabled]="readOnly"
             autocomplete="off">
      @if (dropdownOpen && filteredTimezones.length > 0) {
        <div class="tz-dropdown">
          @for (tz of filteredTimezones; track tz) {
            <div class="tz-option"
                 [class.highlighted]="tz === highlightedTz"
                 (mousedown)="selectTimezone(tz)">
              {{ tz }}
            </div>
          }
        </div>
      }
      @if (validationError) {
        <div class="tz-error">{{ validationError }}</div>
      }
    </div>
  `,
    styles: [`
    .param-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
    .param-label { font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); margin: 0; }
    .required { color: var(--cwc-error-color); }
    .param-description { font-size: 0.6875rem; color: hsl(0,0%,58%); margin-bottom: 6px; }
    .param-input {
      background: hsl(0,0%,9%);
      border: 1px solid hsl(0,0%,24%);
      color: hsl(0,0%,96%);
      font-size: 0.8125rem;
      border-radius: 6px;
    }
    .param-input:focus { background: hsl(0,0%,9%); border-color: hsl(247,49%,53%); box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15); color: hsl(0,0%,96%); }
    .tz-wrapper { position: relative; }
    .tz-dropdown {
      position: absolute;
      top: 100%;
      left: 0;
      right: 0;
      z-index: 200;
      max-height: 200px;
      overflow-y: auto;
      background: hsl(0,0%,13%);
      border: 1px solid hsl(0,0%,24%);
      border-top: none;
      border-radius: 0 0 6px 6px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.4);
    }
    .tz-option {
      padding: 6px 10px;
      font-size: 0.8125rem;
      color: hsl(0,0%,90%);
      cursor: pointer;
      white-space: nowrap;
    }
    .tz-option:hover, .tz-option.highlighted {
      background: hsl(0,0%,20%);
    }
    .tz-error {
      font-size: 0.6875rem;
      color: hsl(0,72%,65%);
      margin-top: 3px;
    }
  `]
})
export class TimezoneParamComponent implements OnInit {
  @Input() param!: NodeParameter;
  @Input() value: any = 'UTC';
  @Input() readOnly = false;
  @Output() valueChange = new EventEmitter<any>();
  @Output() blurred = new EventEmitter<void>();
  @Output() focused = new EventEmitter<void>();

  displayValue = 'UTC';
  dropdownOpen = false;
  highlightedTz = '';
  validationError = '';
  filteredTimezones: string[] = [];

  private allTimezones: string[] = [];
  private isFocused = false;

  ngOnInit(): void {
    this.allTimezones = this.getSupportedTimezones();
    this.displayValue = this.value || 'UTC';
  }

  onInput(val: string): void {
    this.displayValue = val;
    this.validationError = '';
    const query = val.toLowerCase();
    this.filteredTimezones = query
      ? this.allTimezones.filter(tz => tz.toLowerCase().includes(query))
      : this.allTimezones;
    this.dropdownOpen = true;
    this.highlightedTz = this.filteredTimezones.length > 0 ? this.filteredTimezones[0] : '';
  }

  onFocus(): void {
    this.isFocused = true;
    this.focused.emit();
    const query = (this.displayValue || '').toLowerCase();
    this.filteredTimezones = query
      ? this.allTimezones.filter(tz => tz.toLowerCase().includes(query))
      : this.allTimezones;
    this.dropdownOpen = true;
    this.highlightedTz = '';
    this.validationError = '';
  }

  onBlur(): void {
    this.isFocused = false;
    // Short delay to allow mousedown on dropdown to fire first
    setTimeout(() => {
      this.dropdownOpen = false;
      this.validateAndEmit();
      this.blurred.emit();
    }, 150);
  }

  selectTimezone(tz: string): void {
    this.displayValue = tz;
    this.validationError = '';
    this.dropdownOpen = false;
    this.valueChange.emit(tz);
  }

  private validateAndEmit(): void {
    const val = (this.displayValue || '').trim();
    if (!val) {
      this.displayValue = this.param.defaultValue ?? 'UTC';
      this.valueChange.emit(this.displayValue);
      return;
    }
    // Case-insensitive match against supported timezones
    const match = this.allTimezones.find(tz => tz.toLowerCase() === val.toLowerCase());
    if (match) {
      this.displayValue = match;
      this.validationError = '';
      this.valueChange.emit(match);
    } else {
      this.displayValue = this.param.defaultValue ?? 'UTC';
      this.validationError = `"${val}" is not a valid timezone. Reset to ${this.displayValue}.`;
      this.valueChange.emit(this.displayValue);
    }
  }

  private getSupportedTimezones(): string[] {
    try {
      return (Intl as any).supportedValuesOf('timeZone');
    } catch {
      // Fallback for older browsers
      return [
        'UTC', 'GMT',
        'US/Eastern', 'US/Central', 'US/Mountain', 'US/Pacific',
        'America/New_York', 'America/Chicago', 'America/Denver', 'America/Los_Angeles',
        'America/Toronto', 'America/Vancouver', 'America/Sao_Paulo', 'America/Mexico_City',
        'Europe/London', 'Europe/Paris', 'Europe/Berlin', 'Europe/Madrid', 'Europe/Rome',
        'Europe/Amsterdam', 'Europe/Brussels', 'Europe/Zurich', 'Europe/Stockholm',
        'Europe/Moscow', 'Europe/Istanbul',
        'Asia/Tokyo', 'Asia/Shanghai', 'Asia/Hong_Kong', 'Asia/Singapore',
        'Asia/Seoul', 'Asia/Kolkata', 'Asia/Dubai', 'Asia/Bangkok',
        'Australia/Sydney', 'Australia/Melbourne', 'Pacific/Auckland',
        'Africa/Cairo', 'Africa/Johannesburg', 'Africa/Lagos',
      ];
    }
  }
}
