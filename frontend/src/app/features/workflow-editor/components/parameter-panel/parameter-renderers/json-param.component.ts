import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';

@Component({
    selector: 'app-json-param',
    imports: [CommonModule, FormsModule],
    template: `
    <label class="param-label">
      {{ param.displayName }}
      @if (param.required) { <span class="required">*</span> }
    </label>
    @if (param.description) {
      <p class="param-description">{{ param.description }}</p>
    }
    <textarea class="form-control param-input json-editor"
              [ngModel]="jsonString"
              (ngModelChange)="onJsonChange($event)"
              [class.is-invalid]="hasError"
              rows="6"
              spellcheck="false"
              [placeholder]="param.placeHolder || '{}'"
              [disabled]="readOnly"></textarea>
    @if (hasError) {
      <div class="invalid-feedback d-block">Invalid JSON</div>
    }
  `,
    styles: [`
    .param-label { display: block; font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); margin-bottom: 4px; }
    .required { color: var(--cwc-error-color); }
    .param-description { font-size: 0.6875rem; color: hsl(0,0%,58%); margin-bottom: 6px; }
    .json-editor {
      font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
      font-size: 0.75rem;
      line-height: 1.5;
      resize: vertical;
      background: hsl(0,0%,9%);
      border: 1px solid hsl(0,0%,24%);
      color: hsl(0,0%,96%);
      border-radius: 6px;
    }
    .json-editor:focus { background: hsl(0,0%,9%); border-color: hsl(247,49%,53%); box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15); color: hsl(0,0%,96%); }
    .json-editor.is-invalid { border-color: var(--cwc-error-color); }
    .invalid-feedback { font-size: 0.6875rem; }
  `]
})
export class JsonParamComponent {
  @Input() param!: NodeParameter;
  @Input() readOnly = false;
  @Input() set value(val: any) {
    if (typeof val === 'object') {
      this.jsonString = JSON.stringify(val, null, 2);
    } else {
      this.jsonString = val || '';
    }
  }
  @Output() valueChange = new EventEmitter<any>();

  jsonString = '';
  hasError = false;

  onJsonChange(str: string): void {
    this.jsonString = str;
    try {
      const parsed = JSON.parse(str);
      this.hasError = false;
      this.valueChange.emit(parsed);
    } catch {
      this.hasError = str.trim().length > 0;
    }
  }
}
