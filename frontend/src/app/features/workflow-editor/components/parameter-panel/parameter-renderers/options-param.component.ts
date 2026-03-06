import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter, ParameterOption } from '../../../../../core/models';
import { NodeTypeService } from '../../../../../core/services/node-type.service';

@Component({
  selector: 'app-options-param',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="param-header">
      <label class="param-label">
        {{ param.displayName }}
        @if (param.required) { <span class="required">*</span> }
      </label>
      @if (!param.noDataExpression) {
        <div class="expr-radio-group" role="radiogroup">
          <label class="expr-radio" [class.active]="!isExpression" (click)="setFixed()">
            <div class="expr-radio-btn">Fixed</div>
          </label>
          <label class="expr-radio" [class.active]="isExpression" (click)="setExpression()">
            <div class="expr-radio-btn">Expression</div>
          </label>
        </div>
      }
    </div>
    @if (param.description) {
      <p class="param-description">{{ param.description }}</p>
    }
    @if (isExpression) {
      <div class="expr-input-wrapper">
        <input type="text"
               class="form-control param-input expr-input"
               [ngModel]="value"
               (ngModelChange)="valueChange.emit($event)"
               (blur)="blurred.emit()"
               (focus)="focused.emit()"
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
      @if (expressionError) {
        <div class="expr-error-msg">{{ expressionError }}</div>
      }
    } @else {
      <select class="form-select param-input"
              [ngModel]="value"
              (ngModelChange)="valueChange.emit($event)"
              [disabled]="readOnly">
        @if (!param.required) {
          <option [ngValue]="null">-- Select --</option>
        }
        @for (opt of effectiveOptions; track opt.value) {
          <option [ngValue]="opt.value">{{ opt.name }}</option>
        }
      </select>
      @if (selectedDescription) {
        <p class="option-description">{{ selectedDescription }}</p>
      }
    }
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
    .param-input option { background: hsl(0,0%,13%); color: hsl(0,0%,96%); }
    .option-description { font-size: 0.6875rem; color: hsl(0,0%,58%); margin-top: 4px; font-style: italic; }
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
    .expr-error-msg {
      font-size: 0.6875rem;
      color: hsl(0,72%,65%);
      margin-top: 3px;
      font-family: 'Consolas','Monaco',monospace;
    }
  `]
})
export class OptionsParamComponent implements OnInit {
  @Input() param!: NodeParameter;
  @Input() value: any;
  @Input() readOnly = false;
  @Input() expressionError = '';
  @Input() projectId = '';
  @Output() valueChange = new EventEmitter<any>();
  @Output() blurred = new EventEmitter<void>();
  @Output() focused = new EventEmitter<void>();
  @Output() openExpressionEditor = new EventEmitter<void>();

  expressionMode = false;
  expressionPlaceholder = 'e.g. {{$json.status}}';
  dynamicOptions: ParameterOption[] | null = null;

  constructor(private nodeTypeService: NodeTypeService) {}

  ngOnInit(): void {
    if (this.param.name === 'agentDefinitionId' && this.projectId) {
      this.nodeTypeService.getAgentOptions(this.projectId).subscribe({
        next: (opts) => {
          this.dynamicOptions = [
            { name: '(None - configure manually)', value: '', description: '' },
            ...opts.map(o => ({ name: o.name, value: o.value, description: '' }))
          ];
        }
      });
    }
  }

  get effectiveOptions(): ParameterOption[] {
    return this.dynamicOptions || this.param.options || [];
  }

  get isExpression(): boolean {
    const str = String(this.value ?? '');
    return str.includes('{{') || this.expressionMode;
  }

  get selectedDescription(): string {
    if (!this.value || this.isExpression) return '';
    const opt = this.effectiveOptions.find(o => o.value === this.value);
    return opt?.description || '';
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
    if (!data || (!data.includes('$json.') && !data.includes('$node['))) return;
    const input = event.target as HTMLInputElement;
    const currentVal = String(this.value ?? '');
    const pos = input?.selectionStart ?? currentVal.length;
    this.valueChange.emit(currentVal.substring(0, pos) + data + currentVal.substring(pos));
  }
}
