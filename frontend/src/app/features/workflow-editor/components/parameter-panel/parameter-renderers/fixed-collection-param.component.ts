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
        <div class="collection-row">
          @if (param.nestedParameters) {
            @for (nested of param.nestedParameters; track nested.name) {
              <div class="row-field">
                <label class="nested-label">{{ nested.displayName }}</label>
                @if (nested.type === 'options' && nested.options) {
                  <select class="form-select param-input"
                          [ngModel]="item[nested.name]"
                          (ngModelChange)="onItemChange($index, nested.name, $event)"
                          [disabled]="readOnly">
                    @for (opt of nested.options; track opt.value) {
                      <option [ngValue]="opt.value">{{ opt.name }}</option>
                    }
                  </select>
                } @else {
                  <input type="text" class="form-control param-input"
                         [ngModel]="item[nested.name]"
                         (ngModelChange)="onItemChange($index, nested.name, $event)"
                         [placeholder]="nested.placeHolder || ''"
                         [disabled]="readOnly">
                }
              </div>
            }
          }
          @if (!readOnly) {
            <button class="btn-row-delete" (click)="removeItem($index)" title="Delete">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
              </svg>
            </button>
          }
        </div>
      }

      @if (!readOnly) {
        <button class="btn btn-sm btn-add" (click)="addItem()">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          Add {{ addButtonLabel }}
        </button>
      }
    </div>
  `,
  styles: [`
    .fixed-collection { }
    .param-label { display: block; font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); margin-bottom: 4px; }
    .param-description { font-size: 0.6875rem; color: hsl(0,0%,58%); margin-bottom: 8px; }
    .collection-row {
      display: flex;
      align-items: flex-end;
      gap: 8px;
      margin-bottom: 8px;
      padding: 10px;
      background: hsl(0,0%,15%);
      border: 1px solid hsl(0,0%,22%);
      border-radius: 6px;
    }
    .row-field { flex: 1; min-width: 0; }
    .nested-label { display: block; font-size: 0.6875rem; color: hsl(0,0%,58%); margin-bottom: 3px; }
    .param-input { background: hsl(0,0%,9%); border: 1px solid hsl(0,0%,24%); color: hsl(0,0%,96%); font-size: 0.8125rem; border-radius: 6px; }
    .param-input:focus { background: hsl(0,0%,9%); border-color: hsl(247,49%,53%); box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15); color: hsl(0,0%,96%); }
    .param-input option { background: hsl(0,0%,13%); }
    .btn-row-delete {
      flex-shrink: 0;
      width: 32px;
      height: 32px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: none;
      border: 1px solid hsl(0,0%,22%);
      border-radius: 6px;
      color: hsl(0,0%,46%);
      cursor: pointer;
      margin-bottom: 1px;
    }
    .btn-row-delete:hover {
      color: hsl(355,83%,52%);
      border-color: hsla(355,83%,52%,0.3);
      background: hsla(355,83%,52%,0.08);
    }
    .btn-add {
      display: flex; align-items: center; gap: 6px;
      color: hsl(7,100%,68%); background: none;
      border: 1px dashed hsla(7,100%,68%,0.3);
      border-radius: 6px; padding: 6px 12px;
      font-size: 0.75rem; width: 100%; justify-content: center;
      cursor: pointer;
    }
    .btn-add:hover { background: hsla(7,100%,68%,0.1); border-color: hsl(7,100%,68%); }
  `]
})
export class FixedCollectionParamComponent {
  @Input() param!: NodeParameter;
  @Input() readOnly = false;
  @Input() set value(val: any) {
    const incoming = Array.isArray(val) ? val : [];
    // Only replace items if the incoming data is structurally different.
    // This prevents the value setter (which fires on every change-detection cycle
    // via the store round-trip) from replacing the items array while the user is
    // actively editing text inputs, which would disrupt ngModel.
    if (!this.itemsMatch(incoming)) {
      this.items = incoming.map((item: any) => ({ ...item }));
    }
  }
  @Output() valueChange = new EventEmitter<any>();

  items: any[] = [];

  get addButtonLabel(): string {
    const name = this.param.displayName || 'Item';
    return name.replace('Parameters', 'Parameter').replace('Fields', 'Field');
  }

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

  /** Shallow-compare each item's properties to detect actual data changes. */
  private itemsMatch(incoming: any[]): boolean {
    if (this.items.length !== incoming.length) return false;
    for (let i = 0; i < incoming.length; i++) {
      const a = this.items[i];
      const b = incoming[i];
      if (!a || !b) return false;
      const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
      for (const key of keys) {
        if (a[key] !== b[key]) return false;
      }
    }
    return true;
  }
}
