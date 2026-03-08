import { Component, Input, Output, EventEmitter, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { McpParameter, McpOutputSchema, McpOutputProperty } from '../../../core/services/settings.service';
import { SettingsService } from '../../../core/services/settings.service';

const AUTO_DESCRIPTIONS: Record<string, string> = {
  query: 'The search query to execute',
  url: 'The URL to process',
  email: 'An email address',
  message: 'The message content',
  name: 'The name',
  id: 'The unique identifier',
  limit: 'Maximum number of results to return',
  offset: 'Number of results to skip',
  filter: 'Filter criteria',
  prompt: 'The prompt text',
  text: 'The text content',
  content: 'The content',
  title: 'The title',
  body: 'The body content',
  subject: 'The subject line',
  description: 'A description',
  input: 'The input data',
  output: 'The output data',
  key: 'The key',
  value: 'The value',
  token: 'The authentication token',
  password: 'The password',
  username: 'The username',
  firstName: 'The first name',
  lastName: 'The last name',
  phone: 'The phone number',
  address: 'The address',
  city: 'The city',
  country: 'The country',
  status: 'The status',
  type: 'The type',
  category: 'The category',
  tag: 'The tag',
  tags: 'The tags',
  date: 'The date',
  startDate: 'The start date',
  endDate: 'The end date',
  page: 'The page number',
  pageSize: 'The number of items per page',
  sort: 'The sort criteria',
  order: 'The sort order',
  format: 'The format',
  language: 'The language',
  source: 'The source',
  target: 'The target'
};

@Component({
    selector: 'app-mcp-param-editor-modal',
    imports: [CommonModule, FormsModule],
    template: `
    <div class="modal-backdrop" (click)="onBackdropClick($event)">
      <div class="modal-panel param-editor-modal">
        <div class="modal-header">
          <h3 class="modal-title">MCP Schema: {{ workflowName }}</h3>
          <button class="modal-close" (click)="onCancel()">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6L6 18M6 6l12 12"/>
            </svg>
          </button>
        </div>

        <!-- Tab bar -->
        <div class="schema-tabs">
          <button class="schema-tab" [class.active]="activeTab === 'input'" (click)="activeTab = 'input'">Input</button>
          <button class="schema-tab" [class.active]="activeTab === 'output'" (click)="activeTab = 'output'">Output</button>
        </div>

        <div class="modal-body">
          <!-- INPUT TAB -->
          @if (activeTab === 'input') {
            <p class="param-help-text">Define the input parameters that MCP clients will see when calling this workflow as a tool. Each parameter becomes a named field in the tool's input schema.</p>

            @if (params.length === 0) {
              <div class="param-empty-state">
                <p>No parameters defined. This workflow uses a generic text input.</p>
                <p>Add parameters manually or use auto-detect to scan the workflow.</p>
              </div>
            }

            @for (param of params; track $index) {
              <div class="param-row">
                <div class="param-row-header">
                  <span class="param-index">{{ $index + 1 }}</span>
                  <div class="param-row-actions">
                    <button class="btn-icon-sm" (click)="moveParamUp($index)" [disabled]="$index === 0" title="Move up">
                      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 15l-6-6-6 6"/></svg>
                    </button>
                    <button class="btn-icon-sm" (click)="moveParamDown($index)" [disabled]="$index === params.length - 1" title="Move down">
                      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 9l6 6 6-6"/></svg>
                    </button>
                    <button class="btn-icon-sm danger" (click)="removeParam($index)" title="Remove">
                      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg>
                    </button>
                  </div>
                </div>
                <div class="param-row-fields">
                  <div class="form-group param-name-group">
                    <label class="form-label">Name</label>
                    <input class="form-input" type="text" [(ngModel)]="param.name" placeholder="paramName" (blur)="onParamNameBlur(param)">
                  </div>
                  <div class="form-group param-type-group">
                    <label class="form-label">Type</label>
                    <select class="form-input" [(ngModel)]="param.type">
                      <option value="string">string</option>
                      <option value="number">number</option>
                      <option value="boolean">boolean</option>
                      <option value="object">object</option>
                      <option value="array">array</option>
                    </select>
                  </div>
                  <div class="form-group param-desc-group">
                    <label class="form-label">Description</label>
                    <input class="form-input" type="text" [(ngModel)]="param.description" placeholder="Describe this parameter">
                  </div>
                  <div class="form-group param-required-group">
                    <label class="form-label">Required</label>
                    <label class="toggle param-toggle">
                      <input type="checkbox" [(ngModel)]="param.required">
                      <span class="toggle-slider"></span>
                    </label>
                  </div>
                </div>
              </div>
            }
          }

          <!-- OUTPUT TAB -->
          @if (activeTab === 'output') {
            <p class="param-help-text">Define the expected output format of this tool. This helps MCP clients understand the response structure.</p>

            <div class="output-config">
              <div class="output-config-row">
                <div class="form-group output-format-group">
                  <label class="form-label">Format</label>
                  <select class="form-input" [(ngModel)]="outputFormat" (ngModelChange)="onOutputFormatChange()">
                    <option value="json">JSON</option>
                    <option value="text">Text</option>
                    <option value="html">HTML</option>
                    <option value="xml">XML</option>
                  </select>
                </div>
                <div class="form-group output-desc-group">
                  <label class="form-label">Description</label>
                  <input class="form-input" type="text" [(ngModel)]="outputDescription" placeholder="Describe the output">
                </div>
              </div>
            </div>

            @if (outputFormat === 'json') {
              @if (outputProperties.length === 0) {
                <div class="param-empty-state">
                  <p>No output properties defined.</p>
                  <p>Add properties to describe the JSON structure returned by this tool.</p>
                </div>
              }

              @for (prop of outputProperties; track $index) {
                <div class="param-row">
                  <div class="param-row-header">
                    <span class="param-index">{{ $index + 1 }}</span>
                    <div class="param-row-actions">
                      <button class="btn-icon-sm" (click)="moveOutputPropUp($index)" [disabled]="$index === 0" title="Move up">
                        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 15l-6-6-6 6"/></svg>
                      </button>
                      <button class="btn-icon-sm" (click)="moveOutputPropDown($index)" [disabled]="$index === outputProperties.length - 1" title="Move down">
                        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 9l6 6 6-6"/></svg>
                      </button>
                      <button class="btn-icon-sm danger" (click)="removeOutputProp($index)" title="Remove">
                        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg>
                      </button>
                    </div>
                  </div>
                  <div class="param-row-fields">
                    <div class="form-group param-name-group">
                      <label class="form-label">Name</label>
                      <input class="form-input" type="text" [(ngModel)]="prop.name" placeholder="propertyName">
                    </div>
                    <div class="form-group param-type-group">
                      <label class="form-label">Type</label>
                      <select class="form-input" [(ngModel)]="prop.type">
                        <option value="string">string</option>
                        <option value="number">number</option>
                        <option value="boolean">boolean</option>
                        <option value="object">object</option>
                        <option value="array">array</option>
                      </select>
                    </div>
                    <div class="form-group output-prop-desc-group">
                      <label class="form-label">Description</label>
                      <input class="form-input" type="text" [(ngModel)]="prop.description" placeholder="Describe this property">
                    </div>
                  </div>
                </div>
              }
            } @else {
              <div class="param-empty-state">
                <p>{{ outputFormat === 'text' ? 'Text output does not require a property schema.'
                    : outputFormat === 'html' ? 'HTML output does not require a property schema.'
                    : 'XML output does not require a property schema.' }}</p>
              </div>
            }
          }
        </div>
        <div class="modal-footer param-editor-footer">
          @if (activeTab === 'input') {
            <button class="btn-cancel btn-sm" (click)="autoDetectParams()" [disabled]="autoDetecting">
              {{ autoDetecting ? 'Detecting...' : 'Auto-detect' }}
            </button>
            <button class="btn-cancel btn-sm" (click)="addParam()">Add parameter</button>
          }
          @if (activeTab === 'output' && outputFormat === 'json') {
            <button class="btn-cancel btn-sm" (click)="addOutputProp()">Add property</button>
          }
          <span class="footer-spacer"></span>
          <button class="btn-cancel" (click)="onCancel()">Cancel</button>
          <button class="btn-save" (click)="onSave()" [disabled]="saving">
            {{ saving ? 'Saving...' : 'Save' }}
          </button>
        </div>
      </div>
    </div>
  `,
    styleUrl: '../.././../features/workflow-editor/components/publish-modal/publish-modal.component.scss',
    styles: [`
    .param-editor-modal {
      width: 680px;
    }
    .modal-body {
      max-height: 60vh;
      overflow-y: auto;
    }
    .schema-tabs {
      display: flex;
      gap: 0;
      padding: 0 20px;
      border-bottom: 1px solid hsl(0, 0%, 20%);
    }
    .schema-tab {
      background: none;
      border: none;
      border-bottom: 2px solid transparent;
      color: hsl(0, 0%, 52%);
      padding: 8px 16px;
      font-size: 0.8125rem;
      font-weight: 500;
      cursor: pointer;
      transition: color 0.15s, border-color 0.15s;
    }
    .schema-tab:hover { color: hsl(0, 0%, 80%); }
    .schema-tab.active {
      color: hsl(0, 0%, 95%);
      border-bottom-color: hsl(247, 49%, 53%);
    }
    .param-help-text {
      font-size: 0.8125rem;
      color: hsl(0, 0%, 52%);
      margin: 0;
      line-height: 1.5;
    }
    .param-empty-state {
      text-align: center;
      padding: 24px 0;
    }
    .param-empty-state p {
      font-size: 0.8125rem;
      color: hsl(0, 0%, 44%);
      margin: 0 0 4px;
    }
    .param-row {
      padding: 12px 14px;
      background: hsl(0, 0%, 10%);
      border: 1px solid hsl(0, 0%, 20%);
      border-radius: 8px;
    }
    .param-row-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;
    }
    .param-index {
      font-size: 0.6875rem;
      font-weight: 700;
      color: hsl(0, 0%, 44%);
      background: hsl(0, 0%, 17%);
      width: 20px;
      height: 20px;
      border-radius: 4px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    .param-row-actions {
      display: flex;
      gap: 2px;
    }
    .param-row-fields {
      display: flex;
      gap: 8px;
      align-items: flex-end;
    }
    .param-row-fields .form-input {
      max-width: none;
      padding: 6px 8px;
      font-size: 0.75rem;
    }
    .param-name-group { flex: 2; }
    .param-type-group { flex: 1.2; }
    .param-desc-group { flex: 3; }
    .output-prop-desc-group { flex: 4; }
    .param-required-group {
      flex: 0 0 auto;
      display: flex;
      flex-direction: column;
      align-items: center;
      min-width: 56px;
    }
    .param-toggle { margin-top: 2px; }
    .output-config {
      margin-bottom: 4px;
    }
    .output-config-row {
      display: flex;
      gap: 12px;
      align-items: flex-end;
    }
    .output-format-group { flex: 0 0 120px; }
    .output-desc-group { flex: 1; }
    .output-config-row .form-input {
      max-width: none;
      padding: 6px 8px;
      font-size: 0.75rem;
    }
    .param-editor-footer {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .footer-spacer { flex: 1; }
    .btn-sm {
      padding: 4px 12px;
      font-size: 0.75rem;
    }
    .btn-icon-sm {
      background: none;
      border: none;
      color: hsl(0, 0%, 50%);
      padding: 4px;
      border-radius: 4px;
      cursor: pointer;
      display: flex;
    }
    .btn-icon-sm:hover { background: hsl(0, 0%, 20%); color: hsl(0, 0%, 80%); }
    .btn-icon-sm.danger:hover { background: hsla(355, 83%, 52%, 0.12); color: hsl(355, 83%, 62%); }
    .btn-icon-sm:disabled { opacity: 0.35; cursor: default; }
    .btn-save {
      padding: 7px 16px;
      background: hsl(247, 49%, 53%);
      border: 1px solid hsl(247, 49%, 53%);
      color: #fff;
      border-radius: 6px;
      font-size: 0.8125rem;
      font-weight: 500;
      cursor: pointer;
    }
    .btn-save:hover { background: hsl(247, 49%, 46%); border-color: hsl(247, 49%, 46%); }
    .btn-save:disabled { opacity: 0.45; cursor: not-allowed; }
    .toggle {
      position: relative;
      display: inline-block;
      width: 40px;
      height: 22px;
    }
    .toggle input { opacity: 0; width: 0; height: 0; }
    .toggle .toggle-slider {
      position: absolute;
      inset: 0;
      background: hsl(0, 0%, 24%);
      border-radius: 22px;
      cursor: pointer;
      transition: background 0.2s ease;
    }
    .toggle .toggle-slider::before {
      content: '';
      position: absolute;
      width: 16px;
      height: 16px;
      left: 3px;
      bottom: 3px;
      background: hsl(0, 0%, 60%);
      border-radius: 50%;
      transition: transform 0.2s ease, background 0.2s ease;
    }
    .toggle input:checked + .toggle-slider { background: hsl(247, 49%, 53%); }
    .toggle input:checked + .toggle-slider::before { transform: translateX(18px); background: #fff; }
    select.form-input {
      appearance: auto;
    }
    select.form-input option { background: hsl(0, 0%, 13%); }
  `]
})
export class McpParamEditorModalComponent implements OnInit {
  @Input() workflowId = '';
  @Input() workflowName = '';
  @Input() mcpInputSchema: McpParameter[] | null = null;
  @Input() mcpOutputSchema: McpOutputSchema | null = null;

  @Output() saved = new EventEmitter<{ inputSchema: McpParameter[]; outputSchema: McpOutputSchema | null }>();
  @Output() cancelled = new EventEmitter<void>();

  activeTab: 'input' | 'output' = 'input';
  params: McpParameter[] = [];
  saving = false;
  autoDetecting = false;

  // Output schema state
  outputFormat: 'json' | 'text' | 'html' | 'xml' = 'json';
  outputDescription = '';
  outputProperties: McpOutputProperty[] = [];

  constructor(private settingsService: SettingsService) {}

  ngOnInit(): void {
    this.params = this.mcpInputSchema
      ? this.mcpInputSchema.map(p => ({ ...p }))
      : [];

    if (this.mcpOutputSchema) {
      this.outputFormat = this.mcpOutputSchema.format || 'json';
      this.outputDescription = this.mcpOutputSchema.description || '';
      this.outputProperties = this.mcpOutputSchema.properties
        ? this.mcpOutputSchema.properties.map(p => ({ ...p }))
        : [];
    }
  }

  // --- Input tab methods ---

  addParam(): void {
    this.params.push({ name: '', type: 'string', description: '', required: true });
  }

  removeParam(index: number): void {
    this.params.splice(index, 1);
  }

  moveParamUp(index: number): void {
    if (index <= 0) return;
    const temp = this.params[index];
    this.params[index] = this.params[index - 1];
    this.params[index - 1] = temp;
  }

  moveParamDown(index: number): void {
    if (index >= this.params.length - 1) return;
    const temp = this.params[index];
    this.params[index] = this.params[index + 1];
    this.params[index + 1] = temp;
  }

  onParamNameBlur(param: McpParameter): void {
    if (param.description || !param.name) return;
    const autoDesc = AUTO_DESCRIPTIONS[param.name];
    if (autoDesc) {
      param.description = autoDesc;
    } else {
      const spaced = param.name.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase();
      param.description = 'The ' + spaced;
    }
  }

  autoDetectParams(): void {
    if (!this.workflowId) return;
    this.autoDetecting = true;
    this.settingsService.autoDetectMcpParams(this.workflowId).subscribe({
      next: (detected) => {
        const existingNames = new Set(this.params.map(p => p.name));
        for (const param of detected) {
          if (!existingNames.has(param.name)) {
            this.params.push({ ...param });
            existingNames.add(param.name);
          }
        }
        this.autoDetecting = false;
      },
      error: () => this.autoDetecting = false
    });
  }

  // --- Output tab methods ---

  onOutputFormatChange(): void {
    // Clear properties when switching away from JSON
    if (this.outputFormat !== 'json') {
      this.outputProperties = [];
    }
  }

  addOutputProp(): void {
    this.outputProperties.push({ name: '', type: 'string', description: '' });
  }

  removeOutputProp(index: number): void {
    this.outputProperties.splice(index, 1);
  }

  moveOutputPropUp(index: number): void {
    if (index <= 0) return;
    const temp = this.outputProperties[index];
    this.outputProperties[index] = this.outputProperties[index - 1];
    this.outputProperties[index - 1] = temp;
  }

  moveOutputPropDown(index: number): void {
    if (index >= this.outputProperties.length - 1) return;
    const temp = this.outputProperties[index];
    this.outputProperties[index] = this.outputProperties[index + 1];
    this.outputProperties[index + 1] = temp;
  }

  // --- Save / Cancel ---

  onSave(): void {
    const validParams = this.params.filter(p => p.name.trim());

    let outputSchema: McpOutputSchema | null = null;
    const validProps = this.outputProperties.filter(p => p.name.trim());
    // Only emit output schema if there's meaningful content
    if (this.outputDescription.trim() || validProps.length > 0) {
      outputSchema = {
        format: this.outputFormat,
        description: this.outputDescription.trim() || undefined,
        properties: this.outputFormat === 'json' ? validProps : undefined
      };
    }

    this.saved.emit({ inputSchema: validParams, outputSchema });
  }

  onCancel(): void {
    this.cancelled.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.onCancel();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.onCancel();
  }
}
