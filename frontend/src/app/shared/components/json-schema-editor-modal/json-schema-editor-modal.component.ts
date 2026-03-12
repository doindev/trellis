import {
  Component, Input, Output, EventEmitter, HostListener, OnInit, ViewChild, ElementRef
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  McpParameter, McpParamType, JsonSchemaObject
} from '../../../core/services/settings.service';
import { WorkflowNode } from '../../../core/models';

// ─── Constants ───────────────────────────────────────────────────

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
  key: 'The key',
  value: 'The value',
  status: 'The status',
  type: 'The type',
  category: 'The category',
  date: 'The date',
  page: 'The page number',
  sort: 'The sort criteria',
  format: 'The format',
};

const PARAM_TYPES: McpParamType[] = ['string', 'number', 'integer', 'boolean', 'object', 'array'];

const REGEX_PRESETS: { name: string; pattern: string }[] = [
  { name: 'Email', pattern: '^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$' },
  { name: 'Phone (US)', pattern: '^\\+?1?[\\-. ]?\\(?[0-9]{3}\\)?[\\-. ]?[0-9]{3}[\\-. ]?[0-9]{4}$' },
  { name: 'Phone (Intl)', pattern: '^\\+[1-9][0-9]{6,14}$' },
  { name: 'URL', pattern: '^https?://[^\\s/$.?#].[^\\s]*$' },
  { name: 'IPv4', pattern: '^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$' },
  { name: 'UUID', pattern: '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' },
  { name: 'Date (YYYY-MM-DD)', pattern: '^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$' },
  { name: 'Hex Color', pattern: '^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$' },
  { name: 'Alphanumeric', pattern: '^[a-zA-Z0-9]+$' },
  { name: 'Slug', pattern: '^[a-z0-9]+(-[a-z0-9]+)*$' },
];

// ─── Conversion Utilities ────────────────────────────────────────

function typesInclude(type: McpParamType | McpParamType[], target: McpParamType): boolean {
  return Array.isArray(type) ? type.includes(target) : type === target;
}

function paramsToJsonSchema(params: McpParameter[]): JsonSchemaObject {
  const properties: Record<string, any> = {};
  const required: string[] = [];
  for (const p of params) {
    if (!p.name.trim()) continue;
    const typeVal = Array.isArray(p.type) && p.type.length === 1 ? p.type[0] : p.type;
    const prop: any = { type: typeVal };
    if (p.description) prop.description = p.description;
    if (p.enum && p.enum.length > 0) prop.enum = [...p.enum];
    if (p.minimum != null) prop.minimum = p.minimum;
    if (p.maximum != null) prop.maximum = p.maximum;
    if (p.minLength != null) prop.minLength = p.minLength;
    if (p.maxLength != null) prop.maxLength = p.maxLength;
    if (p.pattern) prop.pattern = p.pattern;
    if (p.default != null) prop.default = p.default;
    if (typesInclude(p.type, 'object') && p.properties && p.properties.length > 0) {
      const nested = paramsToJsonSchema(p.properties);
      prop.properties = nested.properties;
      if (nested.required && nested.required.length > 0) prop.required = nested.required;
    }
    if (typesInclude(p.type, 'array') && p.items) {
      prop.items = { type: p.items.type };
    }
    properties[p.name] = prop;
    if (p.required) required.push(p.name);
  }
  const schema: JsonSchemaObject = { type: 'object', properties };
  if (required.length > 0) schema.required = required;
  return schema;
}

function jsonSchemaToParams(schema: any): McpParameter[] {
  if (!schema || schema.type !== 'object' || !schema.properties) return [];
  const requiredSet = new Set<string>(schema.required || []);
  const params: McpParameter[] = [];
  for (const [name, def] of Object.entries(schema.properties) as [string, any][]) {
    let paramType: McpParamType | McpParamType[];
    if (Array.isArray(def.type)) {
      const valid = def.type.filter((t: string) => PARAM_TYPES.includes(t as McpParamType)) as McpParamType[];
      paramType = valid.length === 1 ? valid[0] : (valid.length > 0 ? valid : 'string');
    } else {
      paramType = (PARAM_TYPES.includes(def.type) ? def.type : 'string') as McpParamType;
    }
    const param: McpParameter = {
      name,
      type: paramType,
      description: def.description || '',
      required: requiredSet.has(name),
    };
    if (def.enum) param.enum = [...def.enum];
    if (def.minimum != null) param.minimum = def.minimum;
    if (def.maximum != null) param.maximum = def.maximum;
    if (def.minLength != null) param.minLength = def.minLength;
    if (def.maxLength != null) param.maxLength = def.maxLength;
    if (def.pattern) param.pattern = def.pattern;
    if (def.default != null) param.default = def.default;
    const defTypeIncludes = (t: string) => Array.isArray(def.type) ? def.type.includes(t) : def.type === t;
    if (defTypeIncludes('object') && def.properties) {
      param.properties = jsonSchemaToParams(def);
    }
    if (defTypeIncludes('array') && def.items) {
      param.items = { type: def.items.type || 'string' };
    }
    params.push(param);
  }
  return params;
}

function generateExample(schema: any): any {
  if (!schema || typeof schema !== 'object') return null;
  const effectiveType = Array.isArray(schema.type) ? schema.type[0] : schema.type;
  switch (effectiveType) {
    case 'object': {
      if (!schema.properties) return {};
      const obj: Record<string, any> = {};
      for (const [key, prop] of Object.entries(schema.properties) as [string, any][]) {
        obj[key] = generateExample(prop);
      }
      return obj;
    }
    case 'array': {
      const itemExample = schema.items ? generateExample(schema.items) : 'item';
      return [itemExample];
    }
    case 'string': {
      if (schema.enum && schema.enum.length > 0) return schema.enum[0];
      if (schema.default != null) return schema.default;
      return 'string';
    }
    case 'number':
    case 'integer': {
      if (schema.enum && schema.enum.length > 0) return Number(schema.enum[0]) || 0;
      if (schema.default != null) return schema.default;
      if (schema.minimum != null) return schema.minimum;
      return 0;
    }
    case 'boolean':
      if (schema.default != null) return schema.default;
      return false;
    default:
      return null;
  }
}

// ─── Component ───────────────────────────────────────────────────

@Component({
    selector: 'app-json-schema-editor-modal',
    imports: [CommonModule, FormsModule],
    templateUrl: './json-schema-editor-modal.component.html',
    styleUrl: './json-schema-editor-modal.component.scss'
})
export class JsonSchemaEditorModalComponent implements OnInit {
  @Input() schema: any = null;
  @Input() allNodes: WorkflowNode[] = [];
  @Input() mcpInputSchema: any = null;
  @Input() readOnly = false;

  @Output() saved = new EventEmitter<any>();
  @Output() cancelled = new EventEmitter<void>();

  @ViewChild('splitContainer') splitContainer?: ElementRef<HTMLDivElement>;

  // State
  params: McpParameter[] = [];
  expandedParams = new Set<number>();
  copySuccess = false;

  // Preview (right pane)
  previewTab: 'schema' | 'example' = 'schema';
  jsonPreview = '';
  examplePreview = '';

  // Split pane
  splitPercent = 66;
  isDragging = false;

  readonly PARAM_TYPES = PARAM_TYPES;
  readonly REGEX_PRESETS = REGEX_PRESETS;

  // Pattern presets popup
  patternMenuTarget: McpParameter | null = null;
  private patternMenuTimer: any = null;

  // Multi-type dropdown
  typeDropdownTarget: McpParameter | null = null;
  private typeDropdownTimer: any = null;

  /** Whether the "Use workflow schema" button should appear */
  get showWorkflowSchemaBtn(): boolean {
    if (!this.mcpInputSchema) return false;
    if (Array.isArray(this.mcpInputSchema)) return this.mcpInputSchema.length > 0;
    return typeof this.mcpInputSchema === 'object' && Object.keys(this.mcpInputSchema).length > 0;
  }

  ngOnInit(): void {
    if (this.schema && typeof this.schema === 'object' && this.schema.type === 'object') {
      this.params = jsonSchemaToParams(this.schema);
    }
    this.updateJsonPreview();
  }

  // ─── Parameters ───────────────────────────────────────────────

  addParam(parentParams?: McpParameter[]): void {
    const target = parentParams || this.params;
    target.push({ name: '', type: 'string', description: '', required: true });
    this.updateJsonPreview();
  }

  removeParam(index: number, parentParams?: McpParameter[]): void {
    const target = parentParams || this.params;
    target.splice(index, 1);
    this.updateJsonPreview();
  }

  moveParamUp(index: number, parentParams?: McpParameter[]): void {
    const target = parentParams || this.params;
    if (index <= 0) return;
    [target[index], target[index - 1]] = [target[index - 1], target[index]];
    this.updateJsonPreview();
  }

  moveParamDown(index: number, parentParams?: McpParameter[]): void {
    const target = parentParams || this.params;
    if (index >= target.length - 1) return;
    [target[index], target[index + 1]] = [target[index + 1], target[index]];
    this.updateJsonPreview();
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
    this.updateJsonPreview();
  }

  onParamChange(): void {
    this.updateJsonPreview();
  }

  toggleConstraints(index: number): void {
    if (this.expandedParams.has(index)) {
      this.expandedParams.delete(index);
    } else {
      this.expandedParams.add(index);
    }
  }

  isConstraintsExpanded(index: number): boolean {
    return this.expandedParams.has(index);
  }

  onTypeChange(param: McpParameter): void {
    this.onTypesChanged(param);
  }

  // ─── Multi-type helpers ─────────────────────────────────────────

  getTypesArray(param: McpParameter): McpParamType[] {
    return Array.isArray(param.type) ? param.type : [param.type];
  }

  isTypeSelected(param: McpParameter, type: McpParamType): boolean {
    return typesInclude(param.type, type);
  }

  toggleType(param: McpParameter, type: McpParamType): void {
    const types = this.getTypesArray(param);
    const idx = types.indexOf(type);
    if (idx >= 0) {
      if (types.length <= 1) return; // enforce at least one
      types.splice(idx, 1);
    } else {
      types.push(type);
    }
    param.type = types.length === 1 ? types[0] : [...types];
    this.onTypesChanged(param);
  }

  getTypeDisplay(param: McpParameter): string {
    const types = this.getTypesArray(param);
    return types.join(', ');
  }

  toggleTypeDropdown(event: MouseEvent, param: McpParameter): void {
    event.stopPropagation();
    this.clearTypeDropdownTimer();
    if (this.typeDropdownTarget === param) {
      this.typeDropdownTarget = null;
    } else {
      this.typeDropdownTarget = param;
      this.startTypeDropdownTimer();
    }
  }

  onTypeDropdownEnter(): void {
    this.clearTypeDropdownTimer();
  }

  onTypeDropdownLeave(): void {
    this.startTypeDropdownTimer();
  }

  private startTypeDropdownTimer(): void {
    this.clearTypeDropdownTimer();
    this.typeDropdownTimer = setTimeout(() => {
      this.typeDropdownTarget = null;
    }, 2000);
  }

  private clearTypeDropdownTimer(): void {
    if (this.typeDropdownTimer) {
      clearTimeout(this.typeDropdownTimer);
      this.typeDropdownTimer = null;
    }
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    this.typeDropdownTarget = null;
    this.clearTypeDropdownTimer();
  }

  private onTypesChanged(param: McpParameter): void {
    const has = (t: McpParamType) => typesInclude(param.type, t);
    if (!has('object')) param.properties = undefined;
    if (!has('array')) param.items = undefined;
    if (has('object')) param.properties = param.properties || [];
    if (has('array')) param.items = param.items || { type: 'string' };
    if (!has('number') && !has('integer')) {
      param.minimum = undefined;
      param.maximum = undefined;
    }
    if (!has('string')) {
      param.minLength = undefined;
      param.maxLength = undefined;
      param.pattern = undefined;
    }
    this.updateJsonPreview();
  }

  paramTypesInclude(param: McpParameter, type: McpParamType): boolean {
    return typesInclude(param.type, type);
  }

  addEnumValue(param: McpParameter): void {
    if (!param.enum) param.enum = [];
    param.enum.push('');
    this.updateJsonPreview();
  }

  removeEnumValue(param: McpParameter, index: number): void {
    param.enum?.splice(index, 1);
    if (param.enum?.length === 0) param.enum = undefined;
    this.updateJsonPreview();
  }

  trackByIndex(index: number): number { return index; }

  updateJsonPreview(): void {
    const schema = paramsToJsonSchema(this.params);
    this.jsonPreview = JSON.stringify(schema, null, 2);
    this.examplePreview = JSON.stringify(generateExample(schema), null, 2);
  }

  copyPreviewContent(): void {
    const content = this.previewTab === 'schema' ? this.jsonPreview : this.examplePreview;
    navigator.clipboard.writeText(content).then(() => {
      this.copySuccess = true;
      setTimeout(() => this.copySuccess = false, 2000);
    });
  }

  // ─── Workflow Schema ──────────────────────────────────────────

  applyWorkflowSchema(): void {
    if (!this.mcpInputSchema) return;

    // Convert MCP schema to flat JSON Schema properties
    const convert = (params: any[]): { properties: Record<string, any>; required: string[] } => {
      const props: Record<string, any> = {};
      const req: string[] = [];
      for (const p of params) {
        if (!p.name?.trim()) continue;
        const prop: any = { type: p.type || 'string' };
        if (p.description) prop.description = p.description;
        if (p.enum?.length) prop.enum = [...p.enum];
        if (p.minimum != null) prop.minimum = p.minimum;
        if (p.maximum != null) prop.maximum = p.maximum;
        if (p.minLength != null) prop.minLength = p.minLength;
        if (p.maxLength != null) prop.maxLength = p.maxLength;
        if (p.pattern) prop.pattern = p.pattern;
        if (p.default != null) prop.default = p.default;
        if (p.type === 'object' && p.properties?.length) {
          const nested = convert(p.properties);
          prop.properties = nested.properties;
          if (nested.required.length) prop.required = nested.required;
        }
        if (p.type === 'array' && p.items) prop.items = { type: p.items.type };
        props[p.name] = prop;
        if (p.required) req.push(p.name);
      }
      return { properties: props, required: req };
    };

    let allProps: Record<string, any>;
    let allRequired: string[];

    if (Array.isArray(this.mcpInputSchema)) {
      const result = convert(this.mcpInputSchema);
      allProps = result.properties;
      allRequired = result.required;
    } else {
      allProps = { ...(this.mcpInputSchema['properties'] || {}) };
      allRequired = [...(this.mcpInputSchema['required'] || [])];
    }

    // Extract path param names from the webhook node's URL
    const pathParamNames = new Set<string>();
    const webhookNode = this.allNodes.find(n => n.type === 'webhook');
    if (webhookNode?.parameters?.['path']) {
      const pathStr: string = webhookNode.parameters['path'];
      const re = /\{([^:}]+)(?::[^}]+)?\}/g;
      let match: RegExpExecArray | null;
      while ((match = re.exec(pathStr)) !== null) {
        pathParamNames.add(match[1]);
      }
    }

    // Route properties into body / pathParams / queryParams
    const bodyProps: Record<string, any> = {};
    const bodyRequired: string[] = [];
    const pathProps: Record<string, any> = {};
    const pathRequired: string[] = [];
    const queryProps: Record<string, any> = {};
    const queryRequired: string[] = [];
    const hasPayload = 'payload' in allProps;

    for (const [name, propDef] of Object.entries(allProps)) {
      const isReq = allRequired.includes(name);
      if (name === 'payload') {
        if (propDef.properties) {
          Object.assign(bodyProps, propDef.properties);
          if (propDef.required) bodyRequired.push(...propDef.required);
        }
      } else if (pathParamNames.has(name)) {
        pathProps[name] = propDef;
        if (isReq) pathRequired.push(name);
      } else if (hasPayload) {
        queryProps[name] = propDef;
        if (isReq) queryRequired.push(name);
      } else {
        bodyProps[name] = propDef;
        if (isReq) bodyRequired.push(name);
      }
    }

    // Build the validation schema
    const rootProps: Record<string, any> = {};
    const rootRequired: string[] = [];

    if (Object.keys(bodyProps).length > 0) {
      const bodySchema: any = { type: 'object', properties: bodyProps };
      if (bodyRequired.length) bodySchema.required = bodyRequired;
      rootProps['body'] = bodySchema;
      rootRequired.push('body');
    }

    if (Object.keys(pathProps).length > 0) {
      const pathSchema: any = { type: 'object', properties: pathProps };
      if (pathRequired.length) pathSchema.required = pathRequired;
      rootProps['pathParams'] = pathSchema;
      rootRequired.push('pathParams');
    }

    if (Object.keys(queryProps).length > 0) {
      const querySchema: any = { type: 'object', properties: queryProps };
      if (queryRequired.length) querySchema.required = queryRequired;
      rootProps['queryParams'] = querySchema;
    }

    const generatedSchema: any = { type: 'object', properties: rootProps };
    if (rootRequired.length) generatedSchema.required = rootRequired;

    // Convert back to visual params and update
    this.params = jsonSchemaToParams(generatedSchema);
    this.updateJsonPreview();
  }

  // ─── Split Pane Resize ───────────────────────────────────────

  onResizeStart(event: MouseEvent): void {
    event.preventDefault();
    this.isDragging = true;
    const container = this.splitContainer?.nativeElement;
    if (!container) return;

    const onMove = (e: MouseEvent) => {
      const rect = container.getBoundingClientRect();
      const pct = ((e.clientX - rect.left) / rect.width) * 100;
      this.splitPercent = Math.max(25, Math.min(75, pct));
    };
    const onUp = () => {
      this.isDragging = false;
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  }

  // ─── Pattern Presets ─────────────────────────────────────────

  togglePatternMenu(event: MouseEvent, param: McpParameter): void {
    event.stopPropagation();
    this.clearPatternMenuTimer();
    if (this.patternMenuTarget === param) {
      this.patternMenuTarget = null;
    } else {
      this.patternMenuTarget = param;
      this.startPatternMenuTimer();
    }
  }

  applyPatternPreset(param: McpParameter, pattern: string): void {
    param.pattern = pattern;
    this.patternMenuTarget = null;
    this.clearPatternMenuTimer();
    this.onParamChange();
  }

  onPatternMenuEnter(): void {
    this.clearPatternMenuTimer();
  }

  onPatternMenuLeave(): void {
    this.startPatternMenuTimer();
  }

  private startPatternMenuTimer(): void {
    this.clearPatternMenuTimer();
    this.patternMenuTimer = setTimeout(() => {
      this.patternMenuTarget = null;
    }, 2000);
  }

  private clearPatternMenuTimer(): void {
    if (this.patternMenuTimer) {
      clearTimeout(this.patternMenuTimer);
      this.patternMenuTimer = null;
    }
  }

  // ─── Save / Cancel ───────────────────────────────────────────

  onSave(): void {
    const schema = paramsToJsonSchema(this.params.filter(p => p.name.trim()));
    this.saved.emit(schema);
  }

  onCancel(): void {
    this.cancelled.emit();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.onCancel();
  }
}
