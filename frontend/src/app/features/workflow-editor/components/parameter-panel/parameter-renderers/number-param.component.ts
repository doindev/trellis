import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';

@Component({
  selector: 'app-number-param',
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
      <input type="text"
             class="form-control param-input expr-input"
             [ngModel]="value"
             (ngModelChange)="valueChange.emit($event)"
             [placeholder]="'={{ }}'"
             [disabled]="readOnly">
    } @else {
      <input type="number"
             class="form-control param-input"
             [ngModel]="value"
             (ngModelChange)="valueChange.emit($event)"
             [placeholder]="param.placeHolder || ''"
             [disabled]="readOnly">
    }
  `,
  styles: [`
    .param-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
    .param-label { font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); margin: 0; }
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
  `]
})
export class NumberParamComponent {
  @Input() param!: NodeParameter;
  @Input() value: any = 0;
  @Input() readOnly = false;
  @Output() valueChange = new EventEmitter<any>();

  get isExpression(): boolean {
    return typeof this.value === 'string' && this.value.startsWith('={{');
  }

  setFixed(): void {
    if (this.isExpression) {
      this.valueChange.emit(this.param.defaultValue ?? 0);
    }
  }

  setExpression(): void {
    if (!this.isExpression) {
      this.valueChange.emit('={{ }}');
    }
  }
}
