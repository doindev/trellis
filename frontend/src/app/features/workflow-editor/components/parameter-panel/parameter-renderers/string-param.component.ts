import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';

@Component({
  selector: 'app-string-param',
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
    @if (isMultiline) {
      <textarea class="form-control param-input"
                [ngModel]="value"
                (ngModelChange)="valueChange.emit($event)"
                [placeholder]="param.placeHolder || ''"
                rows="4"></textarea>
    } @else {
      <input type="text"
             class="form-control param-input"
             [ngModel]="value"
             (ngModelChange)="valueChange.emit($event)"
             [placeholder]="param.placeHolder || ''">
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
    .param-input::placeholder { color: hsl(0,0%,46%); }
    .param-input:focus { background: hsl(0,0%,9%); border-color: hsl(247,49%,53%); box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15); color: hsl(0,0%,96%); }
  `]
})
export class StringParamComponent {
  @Input() param!: NodeParameter;
  @Input() value: any = '';
  @Output() valueChange = new EventEmitter<any>();

  get isMultiline(): boolean {
    return this.param.typeOptions?.rows > 1 || this.param.type === 'string' && this.param.typeOptions?.editor === 'code';
  }
}
