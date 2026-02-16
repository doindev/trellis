import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';

@Component({
  selector: 'app-options-param',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <label class="param-label">
      {{ param.displayName }}
      @if (param.required) { <span class="required">*</span> }
    </label>
    @if (param.description) {
      <p class="param-description">{{ param.description }}</p>
    }
    <select class="form-select param-input"
            [ngModel]="value"
            (ngModelChange)="valueChange.emit($event)">
      @if (!param.required) {
        <option [ngValue]="null">-- Select --</option>
      }
      @for (opt of param.options || []; track opt.value) {
        <option [ngValue]="opt.value">{{ opt.name }}</option>
      }
    </select>
    @if (selectedDescription) {
      <p class="option-description">{{ selectedDescription }}</p>
    }
  `,
  styles: [`
    .param-label { display: block; font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); margin-bottom: 4px; }
    .required { color: var(--trellis-error-color); }
    .param-description { font-size: 0.6875rem; color: hsl(0,0%,58%); margin-bottom: 6px; }
    .param-input {
      background: hsl(0,0%,9%);
      border: 1px solid hsl(0,0%,24%);
      color: hsl(0,0%,96%);
      font-size: 0.8125rem;
      border-radius: 6px;
    }
    .param-input:focus { background: hsl(0,0%,9%); border-color: hsl(247,49%,53%); box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15); color: hsl(0,0%,96%); }
    .param-input option { background: hsl(0,0%,13%); color: hsl(0,0%,96%); }
    .option-description { font-size: 0.6875rem; color: hsl(0,0%,58%); margin-top: 4px; font-style: italic; }
  `]
})
export class OptionsParamComponent {
  @Input() param!: NodeParameter;
  @Input() value: any;
  @Output() valueChange = new EventEmitter<any>();

  get selectedDescription(): string {
    if (!this.value || !this.param.options) return '';
    const opt = this.param.options.find(o => o.value === this.value);
    return opt?.description || '';
  }
}
