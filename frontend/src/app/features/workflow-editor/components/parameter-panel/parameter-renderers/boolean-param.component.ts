import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';

@Component({
  selector: 'app-boolean-param',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    @if (isExpression) {
      <div class="param-header">
        <label class="param-label">{{ param.displayName }}</label>
        @if (!param.noDataExpression) {
          <div class="expr-radio-group" role="radiogroup">
            <label class="expr-radio" (click)="setFixed()">
              <div class="expr-radio-btn">Fixed</div>
            </label>
            <label class="expr-radio active" >
              <div class="expr-radio-btn">Expression</div>
            </label>
          </div>
        }
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
      <div class="boolean-param">
        <div class="bool-row">
          <div class="form-check form-switch">
            <input class="form-check-input"
                   type="checkbox"
                   [ngModel]="value"
                   (ngModelChange)="valueChange.emit($event)"
                   [id]="'param-' + param.name"
                   [disabled]="readOnly">
            <label class="form-check-label param-label" [for]="'param-' + param.name">
              {{ param.displayName }}
            </label>
          </div>
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
      </div>
    }
  `,
  styles: [`
    .boolean-param { padding: 2px 0; }
    .bool-row { display: flex; align-items: center; justify-content: space-between; }
    .param-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
    .param-label { font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); }
    .param-description { font-size: 0.6875rem; color: hsl(0,0%,58%); margin: 4px 0 0 2.5em; }
    .form-check-input {
      background-color: hsl(0,0%,24%);
      border-color: hsl(0,0%,30%);
    }
    .form-check-input:checked {
      background-color: hsl(147,83%,44%);
      border-color: hsl(147,83%,44%);
    }
    .param-input {
      background: hsl(0,0%,9%);
      border: 1px solid hsl(0,0%,24%);
      color: hsl(0,0%,96%);
      font-size: 0.8125rem;
      border-radius: 6px;
    }
    .param-input:focus { background: hsl(0,0%,9%); border-color: hsl(247,49%,53%); box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15); color: hsl(0,0%,96%); }
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
export class BooleanParamComponent {
  @Input() param!: NodeParameter;
  @Input() value: any = false;
  @Input() readOnly = false;
  @Output() valueChange = new EventEmitter<any>();
  @Output() openExpressionEditor = new EventEmitter<void>();

  expressionMode = false;
  expressionPlaceholder = 'e.g. {{$json.isActive}}';

  get isExpression(): boolean {
    const str = String(this.value ?? '');
    return str.includes('{{') || this.expressionMode;
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
