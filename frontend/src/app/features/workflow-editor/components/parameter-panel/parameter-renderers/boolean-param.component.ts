import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';

@Component({
  selector: 'app-boolean-param',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="boolean-param">
      <div class="form-check form-switch">
        <input class="form-check-input"
               type="checkbox"
               [ngModel]="value"
               (ngModelChange)="valueChange.emit($event)"
               [id]="'param-' + param.name">
        <label class="form-check-label param-label" [for]="'param-' + param.name">
          {{ param.displayName }}
        </label>
      </div>
      @if (param.description) {
        <p class="param-description">{{ param.description }}</p>
      }
    </div>
  `,
  styles: [`
    .boolean-param { padding: 2px 0; }
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
  `]
})
export class BooleanParamComponent {
  @Input() param!: NodeParameter;
  @Input() value: any = false;
  @Output() valueChange = new EventEmitter<any>();
}
