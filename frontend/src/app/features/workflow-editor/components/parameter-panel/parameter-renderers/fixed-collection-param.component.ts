import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';

@Component({
  selector: 'app-fixed-collection-param',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="fixed-collection">
      <label class="param-label">{{ param.displayName }}</label>
      @if (param.description) {
        <p class="param-description">{{ param.description }}</p>
      }

      @for (item of items; track $index) {
        <div class="collection-item">
          <div class="item-header">
            <span class="item-index">Item {{ $index + 1 }}</span>
            @if (!readOnly) {
              <button class="btn btn-sm btn-ghost" (click)="removeItem($index)">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            }
          </div>
          @if (param.nestedParameters) {
            @for (nested of param.nestedParameters; track nested.name) {
              <div class="nested-param">
                <label class="nested-label">{{ nested.displayName }}</label>
                <input type="text" class="form-control param-input"
                       [ngModel]="item[nested.name]"
                       (ngModelChange)="onItemChange($index, nested.name, $event)"
                       [placeholder]="nested.placeHolder || ''"
                       [disabled]="readOnly">
              </div>
            }
          }
        </div>
      }

      @if (!readOnly) {
      <button class="btn btn-sm btn-add" (click)="addItem()">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        Add item
      </button>
      }
    </div>
  `,
  styles: [`
    .fixed-collection { border: 1px solid hsl(0,0%,24%); border-radius: 6px; padding: 12px; }
    .param-label { display: block; font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); margin-bottom: 4px; }
    .param-description { font-size: 0.6875rem; color: hsl(0,0%,58%); margin-bottom: 8px; }
    .collection-item { background: hsl(0,0%,17%); border: 1px solid hsl(0,0%,24%); border-radius: 6px; padding: 10px; margin-bottom: 8px; }
    .item-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .item-index { font-size: 0.75rem; color: hsl(0,0%,68%); font-weight: 500; }
    .btn-ghost { background: none; border: none; color: hsl(0,0%,68%); padding: 2px; border-radius: 3px; display: flex; }
    .btn-ghost:hover { color: hsl(355,83%,52%); background: hsl(0,0%,24%); }
    .nested-param { margin-bottom: 8px; }
    .nested-label { display: block; font-size: 0.75rem; color: hsl(0,0%,68%); margin-bottom: 3px; }
    .param-input { background: hsl(0,0%,9%); border: 1px solid hsl(0,0%,24%); color: hsl(0,0%,96%); font-size: 0.8125rem; border-radius: 6px; }
    .param-input:focus { background: hsl(0,0%,9%); border-color: hsl(247,49%,53%); box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15); color: hsl(0,0%,96%); }
    .btn-add { display: flex; align-items: center; gap: 6px; color: hsl(7,100%,68%); background: none; border: 1px dashed hsla(7,100%,68%,0.3); border-radius: 6px; padding: 6px 12px; font-size: 0.75rem; width: 100%; justify-content: center; }
    .btn-add:hover { background: hsla(7,100%,68%,0.1); border-color: hsl(7,100%,68%); }
  `]
})
export class FixedCollectionParamComponent {
  @Input() param!: NodeParameter;
  @Input() readOnly = false;
  @Input() set value(val: any) {
    this.items = Array.isArray(val) ? [...val] : [];
  }
  @Output() valueChange = new EventEmitter<any>();

  items: any[] = [];

  addItem(): void {
    const newItem: Record<string, any> = {};
    if (this.param.nestedParameters) {
      this.param.nestedParameters.forEach(p => {
        newItem[p.name] = p.defaultValue ?? '';
      });
    }
    this.items = [...this.items, newItem];
    this.valueChange.emit(this.items);
  }

  removeItem(index: number): void {
    this.items = this.items.filter((_, i) => i !== index);
    this.valueChange.emit(this.items);
  }

  onItemChange(index: number, name: string, val: any): void {
    this.items = this.items.map((item, i) =>
      i === index ? { ...item, [name]: val } : item
    );
    this.valueChange.emit(this.items);
  }
}
