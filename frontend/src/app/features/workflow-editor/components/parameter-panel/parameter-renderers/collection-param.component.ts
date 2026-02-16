import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';

@Component({
  selector: 'app-collection-param',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="collection-param">
      <div class="collection-header" (click)="expanded = !expanded">
        <svg class="chevron" [class.expanded]="expanded"
             viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="9 18 15 12 9 6"/>
        </svg>
        <span class="param-label">{{ param.displayName }}</span>
      </div>
      @if (param.description) {
        <p class="param-description">{{ param.description }}</p>
      }
      @if (expanded && param.nestedParameters) {
        <div class="collection-body">
          @for (nested of param.nestedParameters; track nested.name) {
            <div class="nested-param">
              <label class="nested-label">{{ nested.displayName }}</label>
              @if (nested.type === 'boolean') {
                <div class="form-check form-switch">
                  <input class="form-check-input" type="checkbox"
                         [ngModel]="getNestedValue(nested.name, nested.defaultValue)"
                         (ngModelChange)="onNestedChange(nested.name, $event)">
                </div>
              } @else if (nested.type === 'options' && nested.options) {
                <select class="form-select param-input"
                        [ngModel]="getNestedValue(nested.name, nested.defaultValue)"
                        (ngModelChange)="onNestedChange(nested.name, $event)">
                  @for (opt of nested.options; track opt.value) {
                    <option [ngValue]="opt.value">{{ opt.name }}</option>
                  }
                </select>
              } @else {
                <input type="text" class="form-control param-input"
                       [ngModel]="getNestedValue(nested.name, nested.defaultValue)"
                       (ngModelChange)="onNestedChange(nested.name, $event)"
                       [placeholder]="nested.placeHolder || ''">
              }
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .collection-param { border: 1px solid hsl(0,0%,24%); border-radius: 6px; overflow: hidden; }
    .collection-header { display: flex; align-items: center; gap: 6px; padding: 8px 12px; cursor: pointer; background: hsl(0,0%,17%); }
    .collection-header:hover { background: hsl(0,0%,20%); }
    .chevron { color: hsl(0,0%,46%); transition: transform 0.15s; }
    .chevron.expanded { transform: rotate(90deg); }
    .param-label { font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); margin: 0; }
    .param-description { font-size: 0.6875rem; color: hsl(0,0%,58%); margin: 0; padding: 0 12px 8px; }
    .collection-body { padding: 8px 12px; border-top: 1px solid hsl(0,0%,17%); }
    .nested-param { margin-bottom: 10px; }
    .nested-label { display: block; font-size: 0.75rem; color: hsl(0,0%,68%); margin-bottom: 3px; }
    .param-input { background: hsl(0,0%,9%); border: 1px solid hsl(0,0%,24%); color: hsl(0,0%,96%); font-size: 0.8125rem; border-radius: 6px; }
    .param-input:focus { background: hsl(0,0%,9%); border-color: hsl(247,49%,53%); box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15); color: hsl(0,0%,96%); }
    .param-input option { background: hsl(0,0%,13%); }
    .form-check-input { background-color: hsl(0,0%,24%); border-color: hsl(0,0%,30%); }
    .form-check-input:checked { background-color: hsl(147,83%,44%); border-color: hsl(147,83%,44%); }
  `]
})
export class CollectionParamComponent {
  @Input() param!: NodeParameter;
  @Input() value: any = {};
  @Output() valueChange = new EventEmitter<any>();

  expanded = false;

  getNestedValue(name: string, defaultValue: any): any {
    return this.value?.[name] ?? defaultValue;
  }

  onNestedChange(name: string, val: any): void {
    const updated = { ...(this.value || {}), [name]: val };
    this.valueChange.emit(updated);
  }
}
