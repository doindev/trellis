import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';

@Component({
    selector: 'app-multi-options-param',
    imports: [CommonModule, FormsModule],
    template: `
    <label class="param-label">
      {{ param.displayName }}
      @if (param.required) { <span class="required">*</span> }
    </label>
    @if (param.description) {
      <p class="param-description">{{ param.description }}</p>
    }
    <div class="multi-options-list">
      @for (opt of param.options || []; track opt.value) {
        <label class="multi-option">
          <input type="checkbox"
                 class="form-check-input"
                 [checked]="isSelected(opt.value)"
                 (change)="toggleOption(opt.value)"
                 [disabled]="readOnly">
          <span class="option-label">{{ opt.name }}</span>
        </label>
      }
    </div>
  `,
    styles: [`
    .param-label { display: block; font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); margin-bottom: 4px; }
    .required { color: var(--cwc-error-color); }
    .param-description { font-size: 0.6875rem; color: hsl(0,0%,58%); margin-bottom: 6px; }
    .multi-options-list {
      display: flex;
      flex-direction: column;
      gap: 6px;
      background: hsl(0,0%,9%);
      border: 1px solid hsl(0,0%,24%);
      border-radius: 6px;
      padding: 8px 12px;
    }
    .multi-option {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
    }
    .option-label { font-size: 0.8125rem; color: hsl(0,0%,96%); }
    .form-check-input {
      background-color: hsl(0,0%,24%);
      border-color: hsl(0,0%,30%);
      margin: 0;
    }
    .form-check-input:checked {
      background-color: hsl(147,83%,44%);
      border-color: hsl(147,83%,44%);
    }
  `]
})
export class MultiOptionsParamComponent {
  @Input() param!: NodeParameter;
  @Input() value: any[] = [];
  @Input() readOnly = false;
  @Output() valueChange = new EventEmitter<any>();

  isSelected(optValue: any): boolean {
    return Array.isArray(this.value) && this.value.includes(optValue);
  }

  toggleOption(optValue: any): void {
    const current = Array.isArray(this.value) ? [...this.value] : [];
    const idx = current.indexOf(optValue);
    if (idx >= 0) {
      current.splice(idx, 1);
    } else {
      current.push(optValue);
    }
    this.valueChange.emit(current);
  }
}
