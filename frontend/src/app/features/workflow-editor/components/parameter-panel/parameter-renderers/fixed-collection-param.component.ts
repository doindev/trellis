import { Component, Input, Output, EventEmitter, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';

export interface FixedCollectionExpressionEvent {
  index: number;
  fieldName: string;
  value: string;
}

@Component({
    selector: 'app-fixed-collection-param',
    imports: [CommonModule, FormsModule],
    template: `
    <div class="fixed-collection">
      <label class="param-label">{{ param.displayName }}</label>
      @if (param.description) {
        <p class="param-description">{{ param.description }}</p>
      }

      @for (item of items; track $index) {
        <div class="collection-row"
             [class.drag-over]="dragOverIndex === $index"
             [attr.draggable]="dragSourceIndex === $index ? 'true' : 'false'"
             (dragstart)="onDragStart($event, $index)"
             (dragover)="onDragOver($event, $index)"
             (dragleave)="onDragLeave($index)"
             (drop)="onDrop($event, $index)"
             (dragend)="onDragEnd()">
          @if (!readOnly && items.length > 1) {
            <div class="drag-handle"
                 (mousedown)="onHandleMouseDown($index)"
                 (mouseup)="onHandleMouseUp()"
                 title="Drag to reorder">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
                <circle cx="9" cy="5" r="1.5"/><circle cx="15" cy="5" r="1.5"/>
                <circle cx="9" cy="10" r="1.5"/><circle cx="15" cy="10" r="1.5"/>
                <circle cx="9" cy="15" r="1.5"/><circle cx="15" cy="15" r="1.5"/>
                <circle cx="9" cy="20" r="1.5"/><circle cx="15" cy="20" r="1.5"/>
              </svg>
            </div>
          }
          @if (param.nestedParameters) {
            @for (nested of param.nestedParameters; track nested.name) {
              <div class="row-field" [class.row-field-options]="nested.type === 'options' && nested.options">
                <label class="nested-label">{{ nested.displayName }}</label>
                @if (nested.type === 'options' && nested.options) {
                  <select class="form-select param-input"
                          [ngModel]="item[nested.name]"
                          (ngModelChange)="onSelectChange(items.indexOf(item), nested.name, $event)"
                          [disabled]="readOnly">
                    @for (opt of nested.options; track opt.value) {
                      <option [ngValue]="opt.value">{{ opt.name }}</option>
                    }
                  </select>
                } @else {
                  <div class="text-input-wrapper" [class.has-expression]="isExpression(item[nested.name])">
                    <input type="text"
                           class="form-control param-input"
                           [class.expr-input]="isExpression(item[nested.name])"
                           [value]="item[nested.name] ?? ''"
                           (input)="onTextInput(items.indexOf(item), nested.name, $event)"
                           (blur)="emitItems()"
                           [placeholder]="nested.placeHolder || ''"
                           [disabled]="readOnly"
                           (dragover)="onFieldDragOver($event)"
                           (drop)="onFieldDrop($event, items.indexOf(item), nested.name)">
                    @if (!readOnly) {
                      <button class="expr-editor-btn"
                              (click)="onOpenExpressionEditor(items.indexOf(item), nested.name, item[nested.name])"
                              title="Open expression editor">
                        <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2">
                          <path d="M7 8l-4 4 4 4"/><path d="M17 8l4 4-4 4"/>
                        </svg>
                      </button>
                    }
                  </div>
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
      transition: border-color 0.15s ease;
    }
    .collection-row.drag-over {
      border-color: hsl(247,49%,53%);
      border-style: dashed;
    }
    .collection-row[draggable="true"] {
      opacity: 0.5;
    }
    .drag-handle {
      flex-shrink: 0;
      width: 18px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: hsl(0,0%,32%);
      cursor: grab;
      margin-bottom: 1px;
      padding: 4px 0;
      border-radius: 4px;
      align-self: center;
    }
    .drag-handle:hover {
      color: hsl(0,0%,58%);
      background: hsl(0,0%,20%);
    }
    .drag-handle:active {
      cursor: grabbing;
    }
    .row-field { flex: 2; min-width: 0; }
    .row-field-options { flex: 1; }
    .nested-label { display: block; font-size: 0.6875rem; color: hsl(0,0%,58%); margin-bottom: 3px; }
    .text-input-wrapper {
      position: relative;
    }
    .text-input-wrapper .param-input {
      padding-right: 28px;
    }
    .param-input { background: hsl(0,0%,9%); border: 1px solid hsl(0,0%,24%); color: hsl(0,0%,96%); font-size: 0.8125rem; border-radius: 6px; width: 100%; box-sizing: border-box; }
    .param-input:focus { background: hsl(0,0%,9%); border-color: hsl(247,49%,53%); box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15); color: hsl(0,0%,96%); }
    .param-input.expr-input { border-color: hsl(30,80%,50%); font-family: 'Consolas','Monaco',monospace; }
    .param-input option { background: hsl(0,0%,13%); }
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
export class FixedCollectionParamComponent implements OnDestroy {
  @Input() param!: NodeParameter;
  @Input() readOnly = false;
  @Input() set value(val: any) {
    const incoming = Array.isArray(val) ? val : [];
    // Don't replace items while the user has un-emitted text edits.
    // Auto-save replaces the workflow signal with the backend response, which
    // triggers this setter with stale data that doesn't include the pending edit.
    if (this._dirty) return;
    if (!this.itemsMatch(incoming)) {
      this.items = incoming.map((item: any) => ({ ...item }));
    }
  }
  @Output() valueChange = new EventEmitter<any>();
  @Output() openExpressionEditor = new EventEmitter<FixedCollectionExpressionEvent>();

  items: any[] = [];
  private _dirty = false;
  dragSourceIndex: number | null = null;
  dragOverIndex: number | null = null;

  ngOnDestroy(): void {
    // Flush any pending text edits before the component is destroyed
    // (e.g. user deselects the node without blurring the input).
    if (this._dirty) {
      this._dirty = false;
      this.valueChange.emit(this.cloneItems());
    }
  }

  get addButtonLabel(): string {
    const name = this.param.displayName || 'Item';
    return name.replace('Parameters', 'Parameter').replace('Fields', 'Field');
  }

  isExpression(value: any): boolean {
    return typeof value === 'string' && value.includes('{{');
  }

  addItem(): void {
    const newItem: Record<string, any> = {};
    if (this.param.nestedParameters) {
      this.param.nestedParameters.forEach(p => {
        newItem[p.name] = p.defaultValue ?? '';
      });
    }
    this.items = [...this.items, newItem];
    this.valueChange.emit(this.cloneItems());
  }

  removeItem(index: number): void {
    this.items = this.items.filter((_, i) => i !== index);
    this.valueChange.emit(this.cloneItems());
  }

  /** Handle text input — mutate in place and emit immediately. */
  onTextInput(index: number, name: string, event: Event): void {
    if (!this.items[index]) return;
    const val = (event.target as HTMLInputElement).value;
    this.items[index][name] = val;
    this._dirty = true;
    this.valueChange.emit(this.cloneItems());
  }

  /** Handle select change — mutate in place and emit immediately. */
  onSelectChange(index: number, name: string, val: any): void {
    if (!this.items[index]) return;
    this.items[index] = { ...this.items[index], [name]: val };
    this._dirty = false;
    this.valueChange.emit(this.cloneItems());
  }

  /** Emit the current items when a text input loses focus. */
  emitItems(): void {
    if (this._dirty) {
      this._dirty = false;
      this.valueChange.emit(this.cloneItems());
    }
  }

  // --- Expression editor ---

  onOpenExpressionEditor(index: number, fieldName: string, currentValue: any): void {
    this.openExpressionEditor.emit({
      index,
      fieldName,
      value: String(currentValue ?? '')
    });
  }

  /** Called by the parent after the expression editor saves a value. */
  applyExpressionResult(index: number, fieldName: string, expression: string): void {
    if (!this.items[index]) return;
    this.items[index][fieldName] = expression;
    this.valueChange.emit(this.cloneItems());
  }

  // --- Drag-and-drop on text fields ---

  onFieldDragOver(event: DragEvent): void {
    event.preventDefault();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'copy';
    }
  }

  onFieldDrop(event: DragEvent, index: number, fieldName: string): void {
    event.preventDefault();
    event.stopPropagation();
    const data = event.dataTransfer?.getData('text/plain');
    if (!data || (!data.includes('$json.') && !data.includes('$node['))) return;

    const currentVal = String(this.items[index]?.[fieldName] ?? '');
    const input = event.target as HTMLInputElement;
    const pos = input?.selectionStart ?? currentVal.length;
    const before = currentVal.substring(0, pos);
    const after = currentVal.substring(pos);
    const newVal = before + data + after;
    this.items[index][fieldName] = newVal;
    this.valueChange.emit(this.cloneItems());
  }

  // --- Drag-and-drop reordering ---

  onHandleMouseDown(index: number): void {
    this.dragSourceIndex = index;
  }

  onHandleMouseUp(): void {
    // If drag didn't actually start, clear the source
    this.dragSourceIndex = null;
  }

  onDragStart(event: DragEvent, index: number): void {
    if (this.dragSourceIndex !== index) {
      event.preventDefault();
      return;
    }
    event.dataTransfer!.effectAllowed = 'move';
    event.dataTransfer!.setData('text/plain', String(index));
  }

  onDragOver(event: DragEvent, index: number): void {
    if (this.dragSourceIndex === null || this.dragSourceIndex === index) {
      this.dragOverIndex = null;
      return;
    }
    event.preventDefault();
    event.dataTransfer!.dropEffect = 'move';
    this.dragOverIndex = index;
  }

  onDragLeave(index: number): void {
    if (this.dragOverIndex === index) {
      this.dragOverIndex = null;
    }
  }

  onDrop(event: DragEvent, targetIndex: number): void {
    event.preventDefault();
    const sourceIndex = this.dragSourceIndex;
    this.dragSourceIndex = null;
    this.dragOverIndex = null;
    if (sourceIndex === null || sourceIndex === targetIndex) return;

    const reordered = [...this.items];
    const [moved] = reordered.splice(sourceIndex, 1);
    reordered.splice(targetIndex, 0, moved);
    this.items = reordered;
    this.valueChange.emit(this.cloneItems());
  }

  onDragEnd(): void {
    this.dragSourceIndex = null;
    this.dragOverIndex = null;
  }

  /** Deep-copy items so the store gets its own objects. */
  private cloneItems(): any[] {
    return this.items.map(item => ({ ...item }));
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
