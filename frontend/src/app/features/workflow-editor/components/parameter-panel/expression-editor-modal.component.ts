import {
  Component, Input, Output, EventEmitter, OnInit, OnDestroy, ViewChild, ElementRef
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, switchMap } from 'rxjs/operators';
import { WorkflowService } from '../../../../core/services';
import { SchemaNode } from './parameter-panel.component';

@Component({
  selector: 'app-expression-editor-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="expr-modal-backdrop" (click)="onBackdropClick($event)">
      <div class="expr-modal">
        <!-- Header -->
        <div class="expr-modal-header">
          <h4 class="expr-modal-title">Expression Editor</h4>
          <div class="expr-modal-actions">
            <button class="expr-btn expr-btn-save" (click)="onSave()">Save</button>
            <button class="expr-btn expr-btn-cancel" (click)="onCancel()">Cancel</button>
          </div>
        </div>

        <!-- Body: 3 columns -->
        <div class="expr-modal-body">
          <!-- Left: Input Schema -->
          <div class="expr-col expr-col-schema">
            <div class="expr-col-header">
              <span class="expr-col-label">Input Data</span>
            </div>
            <div class="expr-col-search">
              <input type="text" class="expr-search-input" placeholder="Search fields..."
                     [(ngModel)]="schemaSearch">
            </div>
            <div class="expr-col-body">
              @if (filteredSchemaNodes.length === 0) {
                <div class="expr-empty">{{ schemaSearch ? 'No matching fields' : 'No input data' }}</div>
              } @else {
                @for (node of filteredSchemaNodes; track node.path) {
                  <div class="expr-schema-row"
                       [style.padding-left.px]="node.level * 16 + 8"
                       [class.leaf]="!node.children || node.children.length === 0"
                       [attr.draggable]="(!node.children || node.children.length === 0) ? 'true' : null"
                       (dragstart)="onSchemaDragStart($event, node)"
                       (click)="onSchemaClick(node)">
                    @if (node.children && node.children.length > 0) {
                      <button class="expr-schema-expand"
                              [class.collapsed]="schemaCollapsed.has(node.path)"
                              (click)="toggleSchemaCollapse(node.path); $event.stopPropagation()">
                        <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2">
                          <polyline points="9 18 15 12 9 6"/>
                        </svg>
                      </button>
                    } @else {
                      <span class="expr-schema-spacer"></span>
                    }
                    <span class="expr-schema-type" [attr.data-type]="node.type">{{ typeIcon(node.type) }}</span>
                    <span class="expr-schema-key">{{ node.key }}</span>
                    <span class="expr-schema-value">{{ node.value }}</span>
                  </div>
                }
              }
            </div>
          </div>

          <!-- Center: Expression textarea -->
          <div class="expr-col expr-col-editor">
            <div class="expr-col-header">
              <span class="expr-col-label">Value</span>
              <span class="expr-col-hint">{{ hintText }}</span>
            </div>
            <div class="expr-col-body expr-editor-body">
              <textarea #exprTextarea
                        class="expr-textarea"
                        [(ngModel)]="currentExpression"
                        (ngModelChange)="onExpressionChange($event)"
                        (keydown)="onKeydown($event)"
                        [placeholder]="placeholderText"
                        spellcheck="false"></textarea>
              <div #textMirror class="expr-textarea-mirror" aria-hidden="true"></div>
              @if (showAutocomplete && autocompleteSuggestions.length > 0) {
                <div class="autocomplete-dropdown"
                     [style.top.px]="autocompleteTop"
                     [style.left.px]="autocompleteLeft">
                  @for (suggestion of autocompleteSuggestions; track suggestion; let i = $index) {
                    <div class="autocomplete-item"
                         [class.selected]="i === autocompleteSelectedIndex"
                         (mousedown)="applySuggestion(suggestion); $event.preventDefault()"
                         (mouseenter)="autocompleteSelectedIndex = i">
                      {{ suggestion }}
                    </div>
                  }
                </div>
              }
            </div>
          </div>

          <!-- Right: Result -->
          <div class="expr-col expr-col-result">
            <div class="expr-col-header">
              <span class="expr-col-label">Result</span>
              <div class="expr-display-toggle">
                <button class="expr-mode-btn" [class.active]="displayMode === 'text'" (click)="displayMode = 'text'">Text</button>
                <button class="expr-mode-btn" [class.active]="displayMode === 'html'" (click)="displayMode = 'html'">HTML</button>
              </div>
            </div>
            <div class="expr-col-body">
              @if (isEvaluating) {
                <div class="expr-evaluating">Evaluating...</div>
              } @else if (evaluationError) {
                <div class="expr-error">{{ evaluationError }}</div>
              } @else if (displayMode === 'html' && evaluationResult != null) {
                <div class="expr-result-html" [innerHTML]="formatResult()"></div>
              } @else {
                <pre class="expr-result-text">{{ formatResult() }}</pre>
              }
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .expr-modal-backdrop {
      position: fixed;
      inset: 0;
      z-index: 1070;
      background: rgba(0, 0, 0, 0.6);
      display: flex;
      align-items: stretch;
      justify-content: stretch;
    }
    .expr-modal {
      position: fixed;
      inset: 60px;
      z-index: 1071;
      background: hsl(0, 0%, 11%);
      border-radius: 12px;
      display: flex;
      flex-direction: column;
      border: 1px solid hsl(0, 0%, 22%);
      overflow: hidden;
    }
    .expr-modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      height: 48px;
      padding: 0 16px;
      border-bottom: 1px solid hsl(0, 0%, 22%);
      flex-shrink: 0;
    }
    .expr-modal-title {
      font-size: 0.875rem;
      font-weight: 600;
      color: hsl(0, 0%, 90%);
      margin: 0;
    }
    .expr-modal-actions {
      display: flex;
      gap: 8px;
    }
    .expr-btn {
      padding: 5px 14px;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 500;
      border: none;
      cursor: pointer;
      transition: background 0.15s;
    }
    .expr-btn-save {
      background: hsl(147, 64%, 32%);
      color: hsl(0, 0%, 96%);
    }
    .expr-btn-save:hover { background: hsl(147, 64%, 38%); }
    .expr-btn-cancel {
      background: hsl(0, 0%, 20%);
      color: hsl(0, 0%, 80%);
    }
    .expr-btn-cancel:hover { background: hsl(0, 0%, 26%); }

    .expr-modal-body {
      flex: 1;
      display: flex;
      overflow: hidden;
    }
    .expr-col {
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    .expr-col-schema { width: 25%; border-right: 1px solid hsl(0, 0%, 22%); }
    .expr-col-editor { width: 50%; border-right: 1px solid hsl(0, 0%, 22%); }
    .expr-col-result { width: 25%; }

    .expr-col-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      height: 38px;
      padding: 0 12px;
      border-bottom: 1px solid hsl(0, 0%, 22%);
      flex-shrink: 0;
    }
    .expr-col-label {
      font-size: 0.6875rem;
      font-weight: 600;
      color: hsl(0, 0%, 58%);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .expr-col-search {
      padding: 8px 8px 4px;
      flex-shrink: 0;
    }
    .expr-search-input {
      width: 100%;
      background: hsl(0, 0%, 8%);
      border: 1px solid hsl(0, 0%, 22%);
      color: hsl(0, 0%, 90%);
      font-size: 0.75rem;
      padding: 5px 8px;
      border-radius: 4px;
      outline: none;
    }
    .expr-search-input:focus { border-color: hsl(247, 49%, 53%); }
    .expr-search-input::placeholder { color: hsl(0, 0%, 38%); }

    .expr-col-body {
      flex: 1;
      overflow-y: auto;
      padding: 4px 0;
    }

    /* Schema rows */
    .expr-schema-row {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 3px 8px;
      min-height: 26px;
      cursor: default;
      font-size: 0.8125rem;
    }
    .expr-schema-row.leaf {
      cursor: pointer;
    }
    .expr-schema-row.leaf:hover {
      background: hsl(0, 0%, 17%);
    }
    .expr-schema-row[draggable="true"] {
      cursor: grab;
    }
    .expr-schema-row[draggable="true"]:active {
      cursor: grabbing;
    }
    .expr-schema-expand {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 16px;
      height: 16px;
      background: none;
      border: none;
      color: hsl(0, 0%, 50%);
      cursor: pointer;
      padding: 0;
      flex-shrink: 0;
      transition: transform 0.15s;
      transform: rotate(90deg);
    }
    .expr-schema-expand.collapsed { transform: rotate(0deg); }
    .expr-schema-expand:hover { color: hsl(0, 0%, 90%); }
    .expr-schema-spacer { width: 16px; flex-shrink: 0; }
    .expr-schema-type {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 20px;
      height: 16px;
      padding: 0 3px;
      border-radius: 3px;
      font-size: 0.5625rem;
      font-weight: 700;
      font-family: 'Consolas', 'Monaco', monospace;
      flex-shrink: 0;
      background: hsl(0, 0%, 20%);
      color: hsl(0, 0%, 60%);
    }
    .expr-schema-type[data-type="string"] { background: hsla(210, 60%, 50%, 0.15); color: hsl(210, 70%, 65%); }
    .expr-schema-type[data-type="number"] { background: hsla(30, 70%, 50%, 0.15); color: hsl(30, 80%, 65%); }
    .expr-schema-type[data-type="boolean"] { background: hsla(270, 60%, 50%, 0.15); color: hsl(270, 70%, 70%); }
    .expr-schema-type[data-type="object"] { background: hsla(0, 0%, 50%, 0.15); color: hsl(0, 0%, 65%); }
    .expr-schema-type[data-type="array"] { background: hsla(170, 60%, 40%, 0.15); color: hsl(170, 60%, 60%); }
    .expr-schema-key {
      font-weight: 600;
      color: hsl(0, 0%, 88%);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .expr-schema-value {
      color: hsl(0, 0%, 50%);
      font-size: 0.6875rem;
      font-family: 'Consolas', 'Monaco', monospace;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      min-width: 0;
    }

    .expr-col-hint {
      font-size: 0.625rem;
      color: hsl(30, 70%, 55%);
      font-family: 'Consolas', 'Monaco', monospace;
    }

    /* Expression editor */
    .expr-editor-body {
      display: flex;
      flex-direction: column;
      padding: 0 !important;
      position: relative;
    }
    .expr-textarea {
      flex: 1;
      width: 100%;
      background: transparent;
      border: none;
      color: hsl(0, 0%, 90%);
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 0.875rem;
      line-height: 1.5;
      padding: 8px 12px;
      resize: none;
      outline: none;
    }
    .expr-textarea::placeholder { color: hsl(0, 0%, 34%); }

    .expr-textarea-mirror {
      position: absolute;
      top: 0;
      left: 0;
      visibility: hidden;
      white-space: pre-wrap;
      word-wrap: break-word;
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 0.875rem;
      line-height: 1.5;
      padding: 8px 12px;
      width: 100%;
      pointer-events: none;
      overflow: hidden;
    }
    .autocomplete-dropdown {
      position: absolute;
      z-index: 10;
      background: hsl(0, 0%, 15%);
      border: 1px solid hsl(0, 0%, 28%);
      border-radius: 6px;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
      max-height: 200px;
      overflow-y: auto;
      min-width: 160px;
    }
    .autocomplete-item {
      padding: 5px 10px;
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 0.8125rem;
      color: hsl(0, 0%, 80%);
      cursor: pointer;
      white-space: nowrap;
    }
    .autocomplete-item:hover,
    .autocomplete-item.selected {
      background: hsl(247, 49%, 53%, 0.25);
      color: hsl(0, 0%, 96%);
    }

    /* Result */
    .expr-display-toggle {
      display: flex;
      border-radius: 4px;
      overflow: hidden;
      border: 1px solid hsl(0, 0%, 22%);
    }
    .expr-mode-btn {
      padding: 2px 8px;
      font-size: 0.625rem;
      font-weight: 500;
      color: hsl(0, 0%, 50%);
      background: hsl(0, 0%, 11%);
      border: none;
      cursor: pointer;
      transition: all 0.15s;
      white-space: nowrap;
    }
    .expr-mode-btn:hover { color: hsl(0, 0%, 80%); }
    .expr-mode-btn.active { background: hsl(0, 0%, 22%); color: hsl(0, 0%, 96%); }
    .expr-mode-btn + .expr-mode-btn { border-left: 1px solid hsl(0, 0%, 22%); }

    .expr-result-text {
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 0.8125rem;
      color: hsl(0, 0%, 80%);
      margin: 0;
      padding: 12px;
      white-space: pre-wrap;
      word-break: break-word;
    }
    .expr-result-html {
      padding: 12px;
      font-size: 0.8125rem;
      color: hsl(0, 0%, 80%);
      word-break: break-word;
    }
    .expr-evaluating {
      padding: 12px;
      font-size: 0.75rem;
      color: hsl(0, 0%, 50%);
      font-style: italic;
    }
    .expr-error {
      padding: 12px;
      font-size: 0.75rem;
      color: hsl(0, 72%, 65%);
      font-family: 'Consolas', 'Monaco', monospace;
      word-break: break-word;
    }
    .expr-empty {
      padding: 16px 12px;
      font-size: 0.75rem;
      color: hsl(0, 0%, 42%);
      font-style: italic;
    }
  `]
})
export class ExpressionEditorModalComponent implements OnInit, OnDestroy {
  @Input() expression = '';
  @Input() inputItems: any[] = [];
  @Input() nodeNames: string[] = [];

  @Output() expressionSaved = new EventEmitter<string>();
  @Output() closed = new EventEmitter<void>();

  @ViewChild('exprTextarea') exprTextarea?: ElementRef<HTMLTextAreaElement>;
  @ViewChild('textMirror') textMirror?: ElementRef<HTMLDivElement>;

  currentExpression = '';
  evaluationResult: any = null;
  evaluationError = '';
  isEvaluating = false;
  schemaSearch = '';
  schemaCollapsed = new Set<string>();
  displayMode: 'text' | 'html' = 'text';
  hintText = 'Use {{ }} for expressions';
  placeholderText = 'e.g. Hello {{$json.name}}';

  // Autocomplete state
  showAutocomplete = false;
  autocompleteSuggestions: string[] = [];
  autocompleteSelectedIndex = 0;
  autocompleteTop = 0;
  autocompleteLeft = 0;
  private autocompleteTokenStart = 0;

  private evaluateSubject = new Subject<string>();
  private subscription?: Subscription;

  constructor(private workflowService: WorkflowService) {}

  ngOnInit(): void {
    this.currentExpression = this.expression;

    this.subscription = this.evaluateSubject.pipe(
      debounceTime(300),
      switchMap(expr => {
        if (!expr.trim()) {
          this.isEvaluating = false;
          this.evaluationResult = null;
          this.evaluationError = '';
          return [];
        }
        this.isEvaluating = true;
        return this.workflowService.evaluateExpression(expr, this.inputItems);
      })
    ).subscribe({
      next: (res) => {
        this.isEvaluating = false;
        if (res.error) {
          this.evaluationError = res.error;
          this.evaluationResult = null;
        } else {
          this.evaluationResult = res.result;
          this.evaluationError = '';
        }
      },
      error: (err) => {
        this.isEvaluating = false;
        this.evaluationError = err.message || 'Evaluation failed';
        this.evaluationResult = null;
      }
    });

    // Trigger initial evaluation
    if (this.currentExpression.trim()) {
      this.evaluateSubject.next(this.currentExpression);
    }
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  onExpressionChange(value: string): void {
    this.evaluateSubject.next(value);
    setTimeout(() => this.updateAutocomplete());
  }

  onKeydown(event: KeyboardEvent): void {
    if (this.showAutocomplete && this.autocompleteSuggestions.length > 0) {
      if (event.key === 'ArrowDown') {
        event.preventDefault();
        this.autocompleteSelectedIndex = (this.autocompleteSelectedIndex + 1) % this.autocompleteSuggestions.length;
        return;
      } else if (event.key === 'ArrowUp') {
        event.preventDefault();
        this.autocompleteSelectedIndex = (this.autocompleteSelectedIndex - 1 + this.autocompleteSuggestions.length) % this.autocompleteSuggestions.length;
        return;
      } else if (event.key === 'Enter' || event.key === 'Tab') {
        event.preventDefault();
        this.applySuggestion(this.autocompleteSuggestions[this.autocompleteSelectedIndex]);
        return;
      } else if (event.key === 'Escape') {
        event.preventDefault();
        this.showAutocomplete = false;
        return;
      }
    }

    if (event.key === 'Enter' && event.ctrlKey) {
      event.preventDefault();
      this.onSave();
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.onCancel();
    }
  }

  onSave(): void {
    this.expressionSaved.emit(this.currentExpression);
  }

  onCancel(): void {
    this.closed.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('expr-modal-backdrop')) {
      this.closed.emit();
    }
  }

  formatResult(): string {
    if (this.evaluationResult == null) return '';
    if (typeof this.evaluationResult === 'object') {
      return JSON.stringify(this.evaluationResult, null, 2);
    }
    return String(this.evaluationResult);
  }

  // --- Autocomplete ---

  private static readonly GLOBAL_VARS = [
    '$json', '$input', '$node', '$env', '$vars',
    '$execution', '$now', '$today', '$runIndex'
  ];

  updateAutocomplete(): void {
    const ta = this.exprTextarea?.nativeElement;
    if (!ta) { this.showAutocomplete = false; return; }

    const cursorPos = ta.selectionStart;
    const textBefore = this.currentExpression.substring(0, cursorPos);

    // Find the innermost {{ before cursor (not yet closed by }})
    const lastOpen = textBefore.lastIndexOf('{{');
    if (lastOpen < 0) { this.showAutocomplete = false; return; }
    const afterOpen = textBefore.substring(lastOpen + 2);
    if (afterOpen.includes('}}')) { this.showAutocomplete = false; return; }

    // Pattern 1: $node[' or $node[" → suggest node names
    const nodeMatch = afterOpen.match(/\$node\[['"]([^'"]*)$/);
    if (nodeMatch) {
      const partial = nodeMatch[1].toLowerCase();
      this.autocompleteSuggestions = this.nodeNames
        .filter(n => n.toLowerCase().startsWith(partial))
        .slice(0, 15);
      this.autocompleteTokenStart = cursorPos - nodeMatch[1].length;
      this.showSuggestionsAt(cursorPos);
      return;
    }

    // Pattern 2: $json. or $json.nested. → suggest fields from schema
    const jsonMatch = afterOpen.match(/\$json\.([a-zA-Z0-9_.]*?)$/);
    if (jsonMatch) {
      const pathSoFar = jsonMatch[1];
      const parts = pathSoFar.split('.');
      const partial = parts.pop() || '';
      const parentPath = parts.join('.');

      const fields = this.getFieldsAtPath(this.extractSchemaItems(), parentPath);
      this.autocompleteSuggestions = fields
        .filter(f => f.toLowerCase().startsWith(partial.toLowerCase()))
        .slice(0, 15);
      this.autocompleteTokenStart = cursorPos - partial.length;
      this.showSuggestionsAt(cursorPos);
      return;
    }

    // Pattern 3: $ at start or after whitespace/operator → suggest global vars
    const dollarMatch = afterOpen.match(/(?:^|[\s+\-*/%(<>=!&|,])(\$[a-zA-Z]*)$/);
    if (dollarMatch) {
      const partial = dollarMatch[1].toLowerCase();
      this.autocompleteSuggestions = ExpressionEditorModalComponent.GLOBAL_VARS
        .filter(v => v.toLowerCase().startsWith(partial))
        .slice(0, 15);
      this.autocompleteTokenStart = cursorPos - dollarMatch[1].length;
      this.showSuggestionsAt(cursorPos);
      return;
    }

    this.showAutocomplete = false;
  }

  private showSuggestionsAt(cursorPos: number): void {
    if (this.autocompleteSuggestions.length === 0) {
      this.showAutocomplete = false;
      return;
    }
    this.autocompleteSelectedIndex = 0;
    this.positionAutocomplete(cursorPos);
    this.showAutocomplete = true;
  }

  positionAutocomplete(cursorPos: number): void {
    const mirror = this.textMirror?.nativeElement;
    const ta = this.exprTextarea?.nativeElement;
    if (!mirror || !ta) return;

    // Copy text up to cursor into mirror, add a span to measure position
    const textBefore = this.currentExpression.substring(0, cursorPos);
    mirror.textContent = '';
    const textNode = document.createTextNode(textBefore);
    mirror.appendChild(textNode);
    const marker = document.createElement('span');
    marker.textContent = '\u200b'; // zero-width space
    mirror.appendChild(marker);

    const markerRect = marker.getBoundingClientRect();
    const taRect = ta.getBoundingClientRect();

    this.autocompleteTop = markerRect.top - taRect.top + ta.scrollTop + 20;
    this.autocompleteLeft = Math.min(
      markerRect.left - taRect.left,
      ta.clientWidth - 180
    );
  }

  applySuggestion(suggestion: string): void {
    const ta = this.exprTextarea?.nativeElement;
    if (!ta) return;

    const cursorPos = ta.selectionStart;
    const before = this.currentExpression.substring(0, this.autocompleteTokenStart);
    const after = this.currentExpression.substring(cursorPos);

    // For node names, add the closing quote+bracket
    const isNodeSuggestion = /\$node\[['"]$/.test(this.currentExpression.substring(0, this.autocompleteTokenStart));
    let insertText = suggestion;
    if (isNodeSuggestion) {
      const quoteChar = this.currentExpression[this.autocompleteTokenStart - 1] || "'";
      insertText = suggestion + quoteChar + ']';
    }

    this.currentExpression = before + insertText + after;
    this.showAutocomplete = false;
    this.evaluateSubject.next(this.currentExpression);

    setTimeout(() => {
      const newPos = this.autocompleteTokenStart + insertText.length;
      ta.setSelectionRange(newPos, newPos);
      ta.focus();
    });
  }

  /** Walk the input schema tree to get field names at a given dot-notation path */
  private getFieldsAtPath(items: Record<string, any>[], path: string): string[] {
    if (!items || items.length === 0) return [];

    // Merge items into one superset object
    let obj: Record<string, any> = {};
    for (const item of items) {
      if (item && typeof item === 'object' && !Array.isArray(item)) {
        for (const [k, v] of Object.entries(item)) {
          if (!(k in obj)) obj[k] = v;
        }
      }
    }

    if (!path) {
      return Object.keys(obj);
    }

    // Walk the path
    const segments = path.split('.');
    for (const seg of segments) {
      if (obj && typeof obj === 'object' && !Array.isArray(obj) && seg in obj) {
        obj = obj[seg];
      } else {
        return [];
      }
    }

    if (obj && typeof obj === 'object' && !Array.isArray(obj)) {
      return Object.keys(obj);
    }
    return [];
  }

  // --- Schema ---

  get filteredSchemaNodes(): SchemaNode[] {
    const items = this.extractSchemaItems();
    if (items.length === 0) return [];
    const tree = this.buildSchema(items);
    return this.flattenSchema(tree, this.schemaCollapsed, this.schemaSearch);
  }

  private extractSchemaItems(): Record<string, any>[] {
    if (!this.inputItems || this.inputItems.length === 0) return [];
    return this.inputItems.map(item => item?.json ?? item);
  }

  onSchemaClick(node: SchemaNode): void {
    if (node.children && node.children.length > 0) return;
    this.insertAtCursor('{{$json.' + node.path + '}}');
  }

  onSchemaDragStart(event: DragEvent, node: SchemaNode): void {
    if (node.children && node.children.length > 0) return;
    event.dataTransfer?.setData('text/plain', '{{$json.' + node.path + '}}');
    event.dataTransfer!.effectAllowed = 'copy';
  }

  toggleSchemaCollapse(path: string): void {
    if (this.schemaCollapsed.has(path)) {
      this.schemaCollapsed.delete(path);
    } else {
      this.schemaCollapsed.add(path);
    }
  }

  insertAtCursor(text: string): void {
    const ta = this.exprTextarea?.nativeElement;
    if (!ta) {
      this.currentExpression += text;
      this.evaluateSubject.next(this.currentExpression);
      return;
    }
    const start = ta.selectionStart;
    const end = ta.selectionEnd;
    const before = this.currentExpression.substring(0, start);
    const after = this.currentExpression.substring(end);
    this.currentExpression = before + text + after;
    this.evaluateSubject.next(this.currentExpression);
    // Restore cursor after text
    setTimeout(() => {
      const pos = start + text.length;
      ta.setSelectionRange(pos, pos);
      ta.focus();
    });
  }

  typeIcon(type: string): string {
    switch (type) {
      case 'string': return 'Aa';
      case 'number': return '#';
      case 'boolean': return '✓';
      case 'object': return '{}';
      case 'array': return '[]';
      case 'null': return '∅';
      default: return '?';
    }
  }

  // --- Schema building (duplicated from parameter-panel for standalone usage) ---

  private buildSchema(items: Record<string, any>[], parentPath = '', level = 0): SchemaNode[] {
    const merged: Record<string, any> = {};
    for (const item of items) {
      if (item && typeof item === 'object' && !Array.isArray(item)) {
        for (const [k, v] of Object.entries(item)) {
          if (!(k in merged)) merged[k] = v;
        }
      }
    }
    return this.objectToSchema(merged, parentPath, level);
  }

  private objectToSchema(obj: Record<string, any>, parentPath: string, level: number): SchemaNode[] {
    const nodes: SchemaNode[] = [];
    for (const [key, value] of Object.entries(obj)) {
      const path = parentPath ? `${parentPath}.${key}` : key;
      const type = this.typeOf(value);
      const node: SchemaNode = { key, type, path, level, value: '', expanded: true };
      if (type === 'object' && value !== null) {
        node.children = this.objectToSchema(value, path, level + 1);
        node.value = `{${Object.keys(value).length}}`;
      } else if (type === 'array') {
        node.value = `[${(value as any[]).length}]`;
        if ((value as any[]).length > 0 && typeof value[0] === 'object' && value[0] !== null) {
          node.children = this.objectToSchema(value[0], path + '[0]', level + 1);
        }
      } else {
        node.value = value === null ? 'null' : String(value);
      }
      nodes.push(node);
    }
    return nodes;
  }

  private flattenSchema(nodes: SchemaNode[], collapsed: Set<string>, search: string): SchemaNode[] {
    const result: SchemaNode[] = [];
    const term = search.toLowerCase();
    for (const node of nodes) {
      const matches = !search || node.key.toLowerCase().includes(term) || node.value.toLowerCase().includes(term);
      const hasChildren = node.children && node.children.length > 0;
      const childFlat = hasChildren ? this.flattenSchema(node.children!, collapsed, search) : [];
      const childrenMatch = childFlat.length > 0;
      if (matches || childrenMatch) {
        result.push(node);
        if (hasChildren) {
          const isCollapsed = collapsed.has(node.path) && !search;
          if (!isCollapsed) {
            result.push(...childFlat);
          }
        }
      }
    }
    return result;
  }

  private typeOf(value: any): string {
    if (value === null) return 'null';
    if (Array.isArray(value)) return 'array';
    return typeof value;
  }
}
