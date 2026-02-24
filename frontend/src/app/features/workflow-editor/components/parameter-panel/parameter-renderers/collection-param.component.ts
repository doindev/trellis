import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';

@Component({
  selector: 'app-collection-param',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    @if (isExpression) {
      <div class="param-header">
        <label class="param-label">{{ param.displayName }}</label>
        <div class="expr-radio-group" role="radiogroup">
          <label class="expr-radio" (click)="setFixed()">
            <div class="expr-radio-btn">Fixed</div>
          </label>
          <label class="expr-radio active">
            <div class="expr-radio-btn">Expression</div>
          </label>
        </div>
      </div>
      @if (param.description) {
        <p class="param-description">{{ param.description }}</p>
      }
      <div class="expr-input-wrapper">
        <input type="text"
               class="form-control param-input expr-input"
               [ngModel]="value"
               (ngModelChange)="valueChange.emit($event)"
               [placeholder]="expressionPlaceholder"
               [disabled]="readOnly"
               (dragover)="onDragOver($event)"
               (drop)="onDrop($event)">
        <button class="expr-editor-btn" (click)="openExpressionEditor.emit()" title="Open expression editor">
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M7 8l-4 4 4 4"/><path d="M17 8l4 4-4 4"/>
          </svg>
        </button>
      </div>
    } @else {
      <div class="collection-param">
        <div class="collection-header">
          <span class="collection-title">{{ param.displayName }}</span>
          @if (!param.noDataExpression) {
            <div class="expr-radio-group" role="radiogroup">
              <label class="expr-radio active">
                <div class="expr-radio-btn">Fixed</div>
              </label>
              <label class="expr-radio" (click)="setExpression()">
                <div class="expr-radio-btn">Expression</div>
              </label>
            </div>
          }
        </div>
        @if (param.description) {
          <p class="param-description">{{ param.description }}</p>
        }

        <!-- Added properties -->
        @for (nested of addedParams; track nested.name) {
          <div class="added-param">
            <div class="added-param-header">
              <label class="nested-label">{{ nested.displayName }}</label>
              <button class="btn-remove" (click)="removeParam(nested.name)" title="Remove" [disabled]="readOnly">
                <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
            @if (nested.description) {
              <p class="nested-description">{{ nested.description }}</p>
            }
            @if (nested.type === 'boolean') {
              <div class="form-check form-switch">
                <input class="form-check-input" type="checkbox"
                       [ngModel]="getNestedValue(nested.name, nested.defaultValue)"
                       (ngModelChange)="onNestedChange(nested.name, $event)"
                       [disabled]="readOnly">
              </div>
            } @else if (nested.type === 'options' && nested.options) {
              <select class="form-select param-input"
                      [ngModel]="getNestedValue(nested.name, nested.defaultValue)"
                      (ngModelChange)="onNestedChange(nested.name, $event)"
                      [disabled]="readOnly">
                @for (opt of nested.options; track opt.value) {
                  <option [ngValue]="opt.value">{{ opt.name }}</option>
                }
              </select>
            } @else {
              <input type="text" class="form-control param-input"
                     [ngModel]="getNestedValue(nested.name, nested.defaultValue)"
                     (ngModelChange)="onNestedChange(nested.name, $event)"
                     [placeholder]="nested.placeHolder || ''"
                     [disabled]="readOnly">
            }
          </div>
        }

        <!-- No properties message & Add option dropdown -->
        @if (addedParams.length === 0) {
          <div class="no-properties">No properties</div>
        }
        @if (availableParams.length > 0) {
          <div class="add-option-wrapper">
            <select class="form-select param-input add-option-select"
                    [ngModel]="null"
                    (ngModelChange)="addParam($event)"
                    [disabled]="readOnly">
              <option [ngValue]="null" disabled>Add option</option>
              @for (nested of availableParams; track nested.name) {
                <option [ngValue]="nested.name">{{ nested.displayName }}</option>
              }
            </select>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .collection-param { border: 1px solid hsl(0,0%,24%); border-radius: 6px; overflow: hidden; }
    .collection-header {
      display: flex; align-items: center; justify-content: space-between;
      padding: 8px 12px; background: hsl(0,0%,17%);
    }
    .collection-title { font-size: 0.8125rem; font-weight: 600; color: hsl(0,0%,96%); }
    .param-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
    .param-label { font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); margin: 0; flex: 1; }
    .param-description { font-size: 0.6875rem; color: hsl(0,0%,58%); margin: 0; padding: 0 12px 8px; }
    .no-properties {
      padding: 12px;
      text-align: center;
      font-size: 0.8125rem;
      color: hsl(0,0%,46%);
    }
    .add-option-wrapper {
      padding: 8px 12px 12px;
    }
    .add-option-select {
      font-size: 0.8125rem;
    }
    .added-param {
      padding: 8px 12px;
      border-top: 1px solid hsl(0,0%,20%);
    }
    .added-param-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 4px;
    }
    .nested-label { font-size: 0.75rem; color: hsl(0,0%,68%); margin: 0; }
    .nested-description { font-size: 0.625rem; color: hsl(0,0%,50%); margin: 0 0 4px; }
    .btn-remove {
      width: 20px; height: 20px;
      border-radius: 4px;
      border: none;
      background: transparent;
      color: hsl(0,0%,46%);
      cursor: pointer;
      display: flex; align-items: center; justify-content: center;
      padding: 0;
    }
    .btn-remove:hover { color: hsl(0, 60%, 60%); background: hsla(0, 60%, 60%, 0.1); }
    .param-input { background: hsl(0,0%,9%); border: 1px solid hsl(0,0%,24%); color: hsl(0,0%,96%); font-size: 0.8125rem; border-radius: 6px; }
    .param-input:focus { background: hsl(0,0%,9%); border-color: hsl(247,49%,53%); box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15); color: hsl(0,0%,96%); }
    .param-input option { background: hsl(0,0%,13%); }
    .form-check-input { background-color: hsl(0,0%,24%); border-color: hsl(0,0%,30%); }
    .form-check-input:checked { background-color: hsl(147,83%,44%); border-color: hsl(147,83%,44%); }
    .expr-input { border-color: hsl(30,80%,50%); font-family: 'Consolas', 'Monaco', monospace; }
    .expr-radio-group {
      display: inline-flex;
      border-radius: 6px;
      overflow: hidden;
      border: 1px solid hsl(0,0%,24%);
      flex-shrink: 0;
    }
    .expr-radio {
      cursor: pointer;
      margin: 0;
    }
    .expr-radio-btn {
      padding: 2px 8px;
      font-size: 0.6875rem;
      font-weight: 500;
      color: hsl(0,0%,58%);
      background: hsl(0,0%,13%);
      transition: all 0.15s;
      user-select: none;
      white-space: nowrap;
    }
    .expr-radio.active .expr-radio-btn {
      background: hsl(0,0%,20%);
      color: hsl(0,0%,96%);
    }
    .expr-radio:hover .expr-radio-btn {
      color: hsl(0,0%,80%);
    }
    .expr-input-wrapper {
      position: relative;
    }
    .expr-input-wrapper .param-input {
      padding-right: 30px;
    }
    .expr-editor-btn {
      position: absolute;
      right: 4px;
      top: 50%;
      transform: translateY(-50%);
      display: flex;
      align-items: center;
      justify-content: center;
      width: 22px;
      height: 22px;
      background: transparent;
      border: none;
      border-radius: 4px;
      color: hsl(30,80%,50%);
      cursor: pointer;
      opacity: 0.6;
      transition: all 0.15s;
    }
    .expr-editor-btn:hover {
      opacity: 1;
      background: hsla(30,80%,50%,0.15);
    }
  `]
})
export class CollectionParamComponent {
  @Input() param!: NodeParameter;
  @Input() value: any = {};
  @Input() readOnly = false;
  @Input() allParameters: Record<string, any> = {};
  @Output() valueChange = new EventEmitter<any>();
  @Output() openExpressionEditor = new EventEmitter<void>();

  expressionMode = false;
  expressionPlaceholder = 'e.g. {{$json.options}}';

  get isExpression(): boolean {
    const str = String(this.value ?? '');
    return str.includes('{{') || this.expressionMode;
  }

  /** Parameters that have been added (have a value set) */
  get addedParams(): NodeParameter[] {
    if (!this.param.nestedParameters || !this.value || typeof this.value !== 'object') return [];
    return this.param.nestedParameters.filter(p =>
      this.isNestedVisible(p) && this.value.hasOwnProperty(p.name)
    );
  }

  /** Parameters available to add (visible but not yet added) */
  get availableParams(): NodeParameter[] {
    if (!this.param.nestedParameters || typeof this.value !== 'object') return [];
    const currentValue = this.value || {};
    return this.param.nestedParameters.filter(p =>
      this.isNestedVisible(p) && !currentValue.hasOwnProperty(p.name)
    );
  }

  isNestedVisible(nested: NodeParameter): boolean {
    if (!nested.displayOptions) return true;
    const show = nested.displayOptions.show;
    const hide = nested.displayOptions.hide;

    if (show) {
      const visible = Object.entries(show).every(([key, values]: [string, any]) => {
        const currentVal = key.startsWith('/')
          ? this.allParameters[key.substring(1)]
          : this.getNestedValue(key, undefined);
        if (Array.isArray(values)) {
          return values.includes(currentVal);
        }
        return currentVal === values;
      });
      if (!visible) return false;
    }

    if (hide) {
      const hidden = Object.entries(hide).some(([key, values]: [string, any]) => {
        const currentVal = key.startsWith('/')
          ? this.allParameters[key.substring(1)]
          : this.getNestedValue(key, undefined);
        if (Array.isArray(values)) {
          return values.includes(currentVal);
        }
        return currentVal === values;
      });
      if (hidden) return false;
    }

    return true;
  }

  getNestedValue(name: string, defaultValue: any): any {
    return this.value?.[name] ?? defaultValue;
  }

  onNestedChange(name: string, val: any): void {
    const updated = { ...(this.value || {}), [name]: val };
    this.valueChange.emit(updated);
  }

  addParam(name: string): void {
    if (!name) return;
    const nested = this.param.nestedParameters?.find(p => p.name === name);
    if (!nested) return;
    const updated = { ...(this.value || {}), [name]: nested.defaultValue ?? (nested.type === 'boolean' ? false : '') };
    this.valueChange.emit(updated);
  }

  removeParam(name: string): void {
    const updated = { ...(this.value || {}) };
    delete updated[name];
    this.valueChange.emit(updated);
  }

  setFixed(): void {
    this.expressionMode = false;
  }

  setExpression(): void {
    this.expressionMode = true;
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy';
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    const data = event.dataTransfer?.getData('text/plain');
    if (!data || !data.includes('$json.')) return;
    const input = event.target as HTMLInputElement;
    const currentVal = String(this.value ?? '');
    const pos = input?.selectionStart ?? currentVal.length;
    this.valueChange.emit(currentVal.substring(0, pos) + data + currentVal.substring(pos));
  }
}
