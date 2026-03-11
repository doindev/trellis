import { Component, Input, Output, EventEmitter, HostListener, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';
import { FixedCollectionParamComponent } from './fixed-collection-param.component';

@Component({
    selector: 'app-collection-param',
    imports: [CommonModule, FormsModule, FixedCollectionParamComponent],
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
        <button class="expr-editor-btn" (mousedown)="$event.preventDefault(); openExpressionEditor.emit()" title="Open expression editor">
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
            <div class="added-param-row">
              @if (!readOnly) {
                <button class="btn-delete" (click)="removeParam(nested.name)" title="Delete">
                  <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
                    <line x1="10" y1="11" x2="10" y2="17"/>
                    <line x1="14" y1="11" x2="14" y2="17"/>
                  </svg>
                </button>
              }
              <div class="added-param-label-row">
                <label class="nested-label">{{ nested.displayName }}</label>
                @if (!nested.noDataExpression) {
                  <div class="nested-expr-toggle" role="radiogroup">
                    <label class="expr-radio" [class.active]="!isNestedExpression(nested.name)">
                      <div class="expr-radio-btn">Fixed</div>
                    </label>
                    <label class="expr-radio" [class.active]="isNestedExpression(nested.name)">
                      <div class="expr-radio-btn">Expression</div>
                    </label>
                  </div>
                }
              </div>
            </div>
            @if (nested.description) {
              <p class="nested-description">{{ nested.description }}</p>
            }
            <div class="added-param-input">
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
              } @else if (nested.type === 'number') {
                <input type="number" class="form-control param-input"
                       [ngModel]="getNestedValue(nested.name, nested.defaultValue)"
                       (ngModelChange)="onNestedChange(nested.name, $event)"
                       [placeholder]="nested.placeHolder || ''"
                       [disabled]="readOnly"
                       [attr.min]="nested.typeOptions?.minValue"
                       [attr.max]="nested.typeOptions?.maxValue">
              } @else if (nested.type === 'fixedCollection') {
                <app-fixed-collection-param
                  [param]="nested"
                  [value]="getNestedValue(nested.name, [])"
                  [readOnly]="readOnly"
                  (valueChange)="onNestedChange(nested.name, $event)" />
              } @else {
                <input type="text" class="form-control param-input"
                       [ngModel]="getNestedValue(nested.name, nested.defaultValue)"
                       (ngModelChange)="onNestedChange(nested.name, $event)"
                       [placeholder]="nested.placeHolder || ''"
                       [disabled]="readOnly">
              }
            </div>
          </div>
        }

        <!-- No properties message -->
        @if (addedParams.length === 0) {
          <div class="no-properties">No properties</div>
        }

        <!-- Add option dropdown -->
        @if (availableParams.length > 0 && !readOnly) {
          <div class="add-option-area">
            <div class="add-option-trigger" [class.open]="dropdownOpen" (click)="toggleDropdown($event)">
              <span class="add-option-placeholder">Add option</span>
              <svg class="add-option-caret" [class.open]="dropdownOpen"
                   viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="6 9 12 15 18 9"/>
              </svg>
            </div>
            @if (dropdownOpen) {
              <div class="add-option-dropdown">
                @for (nested of availableParams; track nested.name) {
                  <div class="add-option-item" (click)="selectOption(nested.name)">
                    {{ nested.displayName }}
                  </div>
                }
              </div>
            }
          </div>
        }
      </div>
    }
  `,
    styles: [`
    .collection-param { border: 1px solid hsl(0,0%,24%); border-radius: 6px; overflow: visible; }
    .collection-header {
      display: flex; align-items: center; justify-content: space-between;
      padding: 8px 12px; background: hsl(0,0%,17%);
      border-radius: 6px 6px 0 0;
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

    /* Add option styled dropdown */
    .add-option-area {
      padding: 8px 12px 12px;
      position: relative;
    }
    .add-option-trigger {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 6px 10px;
      background: hsl(0,0%,9%);
      border: 1px solid hsl(0,0%,24%);
      border-radius: 6px;
      cursor: pointer;
      transition: border-color 0.15s;
      user-select: none;
    }
    .add-option-trigger:hover {
      border-color: hsl(0,0%,36%);
    }
    .add-option-trigger.open {
      border-color: hsl(247,49%,53%);
      box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15);
    }
    .add-option-placeholder {
      font-size: 0.8125rem;
      color: hsl(0,0%,46%);
    }
    .add-option-caret {
      color: hsl(0,0%,46%);
      transition: transform 0.15s;
      flex-shrink: 0;
    }
    .add-option-caret.open {
      transform: rotate(180deg);
    }
    .add-option-dropdown {
      position: absolute;
      top: calc(100% - 8px);
      left: 12px;
      right: 12px;
      z-index: 20;
      background: hsl(0,0%,13%);
      border: 1px solid hsl(0,0%,26%);
      border-radius: 6px;
      box-shadow: 0 4px 16px rgba(0,0,0,0.4);
      max-height: 200px;
      overflow-y: auto;
      padding: 4px 0;
    }
    .add-option-item {
      padding: 7px 12px;
      font-size: 0.8125rem;
      color: hsl(0,0%,80%);
      cursor: pointer;
      transition: background 0.1s;
    }
    .add-option-item:hover {
      background: hsl(247,49%,53%,0.2);
      color: hsl(0,0%,96%);
    }

    .added-param {
      padding: 8px 12px;
      border-top: 1px solid hsl(0,0%,20%);
    }
    .added-param-row {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 6px;
    }
    .added-param-label-row {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: space-between;
      min-width: 0;
    }
    .added-param-input {
      /* indent to align with label when delete button present */
    }
    .nested-label { font-size: 0.8125rem; font-weight: 400; color: hsl(0,0%,88%); margin: 0; }
    .nested-description { font-size: 0.625rem; color: hsl(0,0%,50%); margin: 0 0 4px; padding-left: 0; }
    .nested-expr-toggle {
      display: inline-flex;
      border-radius: 6px;
      overflow: hidden;
      border: 1px solid hsl(0,0%,24%);
      flex-shrink: 0;
    }
    .btn-delete {
      width: 24px; height: 24px;
      border-radius: 4px;
      border: none;
      background: transparent;
      color: hsl(0,0%,40%);
      cursor: pointer;
      display: flex; align-items: center; justify-content: center;
      padding: 0;
      flex-shrink: 0;
      transition: all 0.15s;
    }
    .btn-delete:hover { color: hsl(0, 60%, 60%); background: hsla(0, 60%, 60%, 0.08); }
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
  dropdownOpen = false;

  constructor(private elRef: ElementRef) {}

  get isExpression(): boolean {
    const str = String(this.value ?? '');
    return str.includes('{{') || this.expressionMode;
  }

  /** Normalize value to a plain object — handles undefined/null/non-object */
  private get safeValue(): Record<string, any> {
    return (this.value && typeof this.value === 'object' && !Array.isArray(this.value)) ? this.value : {};
  }

  /** Parameters that have been added (have a value set) */
  get addedParams(): NodeParameter[] {
    if (!this.param.nestedParameters) return [];
    const v = this.safeValue;
    return this.param.nestedParameters.filter(p =>
      this.isNestedVisible(p) && v.hasOwnProperty(p.name)
    );
  }

  /** Parameters available to add (visible but not yet added) */
  get availableParams(): NodeParameter[] {
    if (!this.param.nestedParameters) return [];
    const v = this.safeValue;
    return this.param.nestedParameters.filter(p =>
      this.isNestedVisible(p) && !v.hasOwnProperty(p.name)
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

  isNestedExpression(name: string): boolean {
    const val = this.value?.[name];
    return typeof val === 'string' && val.includes('{{');
  }

  onNestedChange(name: string, val: any): void {
    const updated = { ...(this.value || {}), [name]: val };
    this.valueChange.emit(updated);
  }

  addParam(name: string): void {
    if (!name) return;
    const nested = this.param.nestedParameters?.find(p => p.name === name);
    if (!nested) return;
    let defaultVal: any;
    if (nested.defaultValue !== undefined && nested.defaultValue !== null) {
      defaultVal = nested.defaultValue;
    } else if (nested.type === 'boolean') {
      defaultVal = false;
    } else if (nested.type === 'fixedCollection') {
      defaultVal = [];
    } else if (nested.type === 'number') {
      defaultVal = 0;
    } else {
      defaultVal = '';
    }
    const updated = { ...(this.value || {}), [name]: defaultVal };
    this.valueChange.emit(updated);
  }

  removeParam(name: string): void {
    const updated = { ...(this.value || {}) };
    delete updated[name];
    this.valueChange.emit(updated);
  }

  // --- Custom dropdown ---

  toggleDropdown(event: MouseEvent): void {
    event.stopPropagation();
    this.dropdownOpen = !this.dropdownOpen;
  }

  selectOption(name: string): void {
    this.dropdownOpen = false;
    this.addParam(name);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.dropdownOpen && !this.elRef.nativeElement.contains(event.target)) {
      this.dropdownOpen = false;
    }
  }

  // --- Expression mode ---

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
    if (!data || (!data.includes('$json.') && !data.includes('$node['))) return;
    const input = event.target as HTMLInputElement;
    const currentVal = String(this.value ?? '');
    const pos = input?.selectionStart ?? currentVal.length;
    this.valueChange.emit(currentVal.substring(0, pos) + data + currentVal.substring(pos));
  }
}
