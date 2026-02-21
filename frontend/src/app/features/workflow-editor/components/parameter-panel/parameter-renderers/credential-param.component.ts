import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CredentialService } from '../../../../../core/services';
import { Credential, CredentialSchema } from '../../../../../core/models';
import { CredentialCreateModalComponent } from '../../../../../shared/components/credential-create-modal/credential-create-modal.component';

@Component({
  selector: 'app-credential-param',
  standalone: true,
  imports: [CommonModule, FormsModule, CredentialCreateModalComponent],
  template: `
    @for (credType of credentialTypes; track credType) {
      <div class="param-group">
        <div class="param-header">
          <label class="param-label">Credential to connect with</label>
        </div>
        <select class="form-select param-input"
                [ngModel]="selectModel[credType]"
                (ngModelChange)="onSelectionChange(credType, $event)"
                [disabled]="readOnly">
          <option [ngValue]="null">-- Select credential --</option>
          @for (cred of getCredentialsForType(credType); track cred.id) {
            <option [ngValue]="cred.id">{{ cred.name }}</option>
          }
          <option value="__create__" class="create-option">+ Create new {{ getTypeDisplayName(credType) }}</option>
        </select>
      </div>
    }
    <app-credential-create-modal
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
  @Output() credentialChanged = new EventEmitter<Record<string, any>>();

  @ViewChild(CredentialCreateModalComponent) createModal!: CredentialCreateModalComponent;

  allCredentials: Credential[] = [];
  credentialSchemas: CredentialSchema[] = [];

  /** Explicitly tracked select values — avoids Angular not re-writing the same value */
  selectModel: Record<string, string | null> = {};

  private pendingCreateType: string | null = null;

  constructor(private credentialService: CredentialService) {}

  ngOnInit(): void {
    this.syncSelectModel();
    this.loadCredentials();
    this.loadTypes();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['currentCredentials']) {
      this.syncSelectModel();
    }
  }

  private syncSelectModel(): void {
    for (const type of this.credentialTypes) {
      this.selectModel[type] = this.currentCredentials?.[type]?.id || null;
    }
  }

  private loadCredentials(): void {
    this.credentialService.list().subscribe({
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

  getCredentialsForType(type: string): Credential[] {
    return this.allCredentials.filter(c => c.type === type);
  }

  getTypeDisplayName(type: string): string {
    const schema = this.credentialSchemas.find(s => s.type === type);
    return schema?.displayName || type;
  }

  onSelectionChange(type: string, value: string | null): void {
    if (value === '__create__') {
      this.pendingCreateType = type;
      // Force select back to previous value so it's not stuck on __create__.
      // Write a sentinel first so Angular detects the subsequent real value as a change.
      this.selectModel = { ...this.selectModel, [type]: '__resetting__' as any };
      setTimeout(() => {
        this.selectModel = { ...this.selectModel, [type]: this.currentCredentials?.[type]?.id || null };
      });
      // Open directly to the matching credential type editor, skip type selection
      this.createModal.openCreateForType(type);
      return;
    }

    this.selectModel[type] = value;
    const updated = { ...this.currentCredentials };
    if (value) {
      updated[type] = { id: value };
    } else {
      delete updated[type];
    }
    this.credentialChanged.emit(updated);
  }

  onCredentialCreated(cred: Credential): void {
    this.allCredentials = [...this.allCredentials, cred];
    if (this.pendingCreateType) {
      const type = this.pendingCreateType;
      const updated = { ...this.currentCredentials };
      updated[type] = { id: cred.id };
      this.selectModel[type] = cred.id!;
      this.credentialChanged.emit(updated);
    }
    this.pendingCreateType = null;
  }

  onCreateClosed(): void {
    this.pendingCreateType = null;
  }
}
