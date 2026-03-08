import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CredentialService } from '../../../../../core/services';
import { Credential, CredentialSchema } from '../../../../../core/models';
import { CredentialCreateModalComponent } from '../../../../../shared/components/credential-create-modal/credential-create-modal.component';

@Component({
    selector: 'app-credential-param',
    imports: [CommonModule, FormsModule, CredentialCreateModalComponent],
    template: `
    <div class="param-group">
      <div class="param-header">
        <label class="param-label">Credential to connect with</label>
      </div>
      <select class="form-select param-input"
              [ngModel]="selectedCredentialId"
              (ngModelChange)="onSelectionChange($event)"
              [disabled]="readOnly">
        <option [ngValue]="null">-- Select credential --</option>
        @for (cred of matchingCredentials; track cred.id) {
          <option [ngValue]="cred.id">{{ cred.name }}{{ showTypeLabel ? ' (' + getTypeDisplayName(cred.type) + ')' : '' }}</option>
        }
        @for (schema of creatableSchemas; track schema.type) {
          <option [value]="'__create__:' + schema.type" class="create-option">+ Create new {{ schema.displayName }}</option>
        }
      </select>
    </div>
    <app-credential-create-modal
      [projectId]="projectId"
      (saved)="onCredentialCreated($event)"
      (closed)="onCreateClosed()" />
  `,
    styles: [`
    .param-group { margin-bottom: 12px; }
    .param-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
    .param-label { font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); margin: 0; }
    .param-input {
      background: hsl(0,0%,9%);
      border: 1px solid hsl(0,0%,24%);
      color: hsl(0,0%,96%);
      font-size: 0.8125rem;
      border-radius: 6px;
    }
    .param-input:focus { background: hsl(0,0%,9%); border-color: hsl(247,49%,53%); box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15); color: hsl(0,0%,96%); }
    .param-input option { background: hsl(0,0%,13%); color: hsl(0,0%,96%); }
    .create-option { color: hsl(247,49%,53%); font-style: italic; }
  `]
})
export class CredentialParamComponent implements OnInit, OnChanges {
  @Input() credentialTypes: string[] = [];
  @Input() currentCredentials: Record<string, any> = {};
  @Input() readOnly = false;
  @Input() projectId = '';
  @Output() credentialChanged = new EventEmitter<Record<string, any>>();

  @ViewChild(CredentialCreateModalComponent) createModal!: CredentialCreateModalComponent;

  allCredentials: Credential[] = [];
  credentialSchemas: CredentialSchema[] = [];
  selectedCredentialId: string | null = null;

  private pendingCreateType: string | null = null;

  constructor(private credentialService: CredentialService) {}

  ngOnInit(): void {
    this.loadCredentials();
    this.loadTypes();
    this.syncSelectedId();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['currentCredentials']) {
      this.syncSelectedId();
    }
  }

  private syncSelectedId(): void {
    if (!this.currentCredentials) {
      this.selectedCredentialId = null;
      return;
    }
    // Check all keys in currentCredentials, not just declared types
    for (const key of Object.keys(this.currentCredentials)) {
      const id = this.currentCredentials[key]?.id;
      if (id) {
        this.selectedCredentialId = id;
        return;
      }
    }
    this.selectedCredentialId = null;
  }

  private loadCredentials(): void {
    const obs = this.projectId
      ? this.credentialService.listByProject(this.projectId)
      : this.credentialService.list();
    obs.subscribe({
      next: (creds) => this.allCredentials = creds,
      error: () => {}
    });
  }

  private loadTypes(): void {
    this.credentialService.listTypes().subscribe({
      next: (types) => this.credentialSchemas = types,
      error: () => {}
    });
  }

  /** Whether the node accepts multiple credential types — show type label to disambiguate */
  get showTypeLabel(): boolean {
    return this.credentialTypes.length > 1;
  }

  /** The category of the node's declared credential types (e.g. "Databases") */
  private get credentialCategory(): string | null {
    for (const type of this.credentialTypes) {
      const schema = this.credentialSchemas.find(s => s.type === type);
      if (schema?.category) return schema.category;
    }
    return null;
  }

  /** All credentials matching the same category as the node's credential types */
  get matchingCredentials(): Credential[] {
    if (this.credentialTypes.length <= 1) {
      // Single type: only show credentials of that exact type
      return this.allCredentials.filter(c => this.credentialTypes.includes(c.type));
    }
    // Multi-type: show all credentials in the same category
    const category = this.credentialCategory;
    if (!category) {
      return this.allCredentials.filter(c => this.credentialTypes.includes(c.type));
    }
    const typesInCategory = new Set(
      this.credentialSchemas.filter(s => s.category === category).map(s => s.type)
    );
    return this.allCredentials.filter(c => typesInCategory.has(c.type));
  }

  /** All credential schemas available for "Create new" */
  get creatableSchemas(): CredentialSchema[] {
    if (this.credentialTypes.length <= 1) {
      // Single type: only offer creating that one type
      return this.credentialSchemas.filter(s => this.credentialTypes.includes(s.type));
    }
    // Multi-type: offer creating any credential in the same category
    const category = this.credentialCategory;
    if (!category) {
      return this.credentialSchemas.filter(s => this.credentialTypes.includes(s.type));
    }
    return this.credentialSchemas
      .filter(s => s.category === category)
      .sort((a, b) => a.displayName.localeCompare(b.displayName));
  }

  getTypeDisplayName(type: string): string {
    const schema = this.credentialSchemas.find(s => s.type === type);
    return schema?.displayName || type;
  }

  /** Find which credential type a given credential id belongs to */
  private findCredentialType(credId: string): string | null {
    const cred = this.allCredentials.find(c => c.id === credId);
    return cred?.type || null;
  }

  onSelectionChange(value: string | null): void {
    if (value && value.startsWith('__create__')) {
      const createType = value.substring('__create__:'.length);
      this.pendingCreateType = createType;
      const prev = this.selectedCredentialId;
      this.selectedCredentialId = '__resetting__' as any;
      setTimeout(() => { this.selectedCredentialId = prev; });
      this.createModal.openCreateForType(createType);
      return;
    }

    this.selectedCredentialId = value;

    const updated: Record<string, any> = {};
    if (value) {
      const type = this.findCredentialType(value);
      if (type) {
        updated[type] = { id: value };
      }
    }
    this.credentialChanged.emit(updated);
  }

  onCredentialCreated(cred: Credential): void {
    this.allCredentials = [...this.allCredentials, cred];
    if (this.pendingCreateType && cred.id) {
      const updated: Record<string, any> = {};
      updated[cred.type] = { id: cred.id };
      this.selectedCredentialId = cred.id;
      this.credentialChanged.emit(updated);
    }
    this.pendingCreateType = null;
  }

  onCreateClosed(): void {
    this.pendingCreateType = null;
  }
}
