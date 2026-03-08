import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CredentialService } from '../../core/services';
import { Credential, CredentialSchema } from '../../core/models';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';

@Component({
    selector: 'app-credential-list',
    imports: [CommonModule, FormsModule, ConfirmDialogComponent, LoadingSpinnerComponent],
    templateUrl: './credential-list.component.html',
    styleUrl: './credential-list.component.scss'
})
export class CredentialListComponent implements OnInit {
  credentials = signal<Credential[]>([]);
  loading = signal(true);
  showDeleteConfirm = signal(false);
  deleteTarget = signal<Credential | null>(null);

  // All available credential types (loaded once)
  credentialTypes = signal<CredentialSchema[]>([]);

  // Modal 1: Type selection
  showTypeSelectModal = signal(false);
  typeSearchTerm = signal('');
  selectedType = signal<CredentialSchema | null>(null);

  filteredTypes = computed(() => {
    const term = this.typeSearchTerm().toLowerCase();
    const types = this.credentialTypes();
    if (!term) return types;
    return types.filter(t =>
      t.displayName.toLowerCase().includes(term) ||
      t.type.toLowerCase().includes(term) ||
      (t.description && t.description.toLowerCase().includes(term))
    );
  });

  // Modal 2: Credential editor
  showEditorModal = signal(false);
  editorCredential = signal<Partial<Credential>>({});
  editorSchema = signal<CredentialSchema | null>(null);
  editorTab = signal<'connection' | 'sharing' | 'details'>('connection');
  isEditing = signal(false);
  saving = signal(false);

  constructor(private credentialService: CredentialService) {}

  ngOnInit(): void {
    this.loadCredentials();
    this.loadTypes();
  }

  loadCredentials(): void {
    this.loading.set(true);
    this.credentialService.list().subscribe({
      next: (data) => {
        this.credentials.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  loadTypes(): void {
    this.credentialService.listTypes().subscribe({
      next: (types) => this.credentialTypes.set(types),
      error: () => {}
    });
  }

  // --- Modal 1: Type selection ---

  openCreateModal(): void {
    this.typeSearchTerm.set('');
    this.selectedType.set(null);
    this.showTypeSelectModal.set(true);
  }

  onTypeSearch(term: string): void {
    this.typeSearchTerm.set(term);
  }

  selectType(type: CredentialSchema): void {
    this.selectedType.set(type);
  }

  continueToEditor(): void {
    const type = this.selectedType();
    if (!type) return;

    // Pre-populate all fields that have default values
    const data: Record<string, any> = {};
    for (const prop of type.properties) {
      if (prop.defaultValue != null) {
        data[prop.name] = prop.defaultValue;
      }
    }

    this.showTypeSelectModal.set(false);
    this.editorCredential.set({
      name: type.displayName + ' account',
      type: type.type,
      data
    });
    this.editorSchema.set(type);
    this.editorTab.set('connection');
    this.isEditing.set(false);
    this.showEditorModal.set(true);
  }

  closeTypeSelectModal(): void {
    this.showTypeSelectModal.set(false);
  }

  // --- Modal 2: Credential editor ---

  openEditModal(cred: Credential): void {
    this.editorCredential.set({ ...cred });
    this.isEditing.set(true);
    this.editorTab.set('connection');
    this.editorSchema.set(null);
    this.showEditorModal.set(true);

    // Load schema for the credential type
    if (cred.type) {
      this.credentialService.getSchema(cred.type).subscribe({
        next: (s) => this.editorSchema.set(s),
        error: () => this.editorSchema.set(null)
      });
    }

    // Load decrypted data so fields are populated for editing
    if (cred.id) {
      this.credentialService.getDecryptedData(cred.id).subscribe({
        next: (data) => {
          const current = this.editorCredential();
          this.editorCredential.set({ ...current, data });
        },
        error: () => {}
      });
    }
  }

  updateCredentialName(name: string): void {
    const cred = this.editorCredential();
    this.editorCredential.set({ ...cred, name });
  }

  onDataFieldChange(fieldName: string, value: any): void {
    const cred = this.editorCredential();
    const data = { ...(cred.data || {}), [fieldName]: value };
    this.editorCredential.set({ ...cred, data });
  }

  saveCredential(): void {
    const cred = this.editorCredential();
    if (!cred.name || !cred.type) return;

    this.saving.set(true);
    const operation = this.isEditing() && cred.id
      ? this.credentialService.update(cred.id, cred)
      : this.credentialService.create(cred);

    operation.subscribe({
      next: () => {
        this.saving.set(false);
        this.showEditorModal.set(false);
        this.loadCredentials();
      },
      error: () => this.saving.set(false)
    });
  }

  closeEditorModal(): void {
    this.showEditorModal.set(false);
  }

  isPasswordField(prop: any): boolean {
    return prop.typeOptions?.password === true || prop.type === 'password';
  }

  isPropertyVisible(prop: any): boolean {
    if (!prop.displayOptions?.show) return true;
    const data = this.editorCredential().data || {};
    for (const [field, allowedValues] of Object.entries(prop.displayOptions.show) as [string, any[]][]) {
      const currentValue = data[field];
      if (!allowedValues.includes(currentValue)) {
        return false;
      }
    }
    return true;
  }

  // --- Delete ---

  confirmDelete(cred: Credential, event: Event): void {
    event.stopPropagation();
    this.deleteTarget.set(cred);
    this.showDeleteConfirm.set(true);
  }

  onDeleteConfirmed(): void {
    const target = this.deleteTarget();
    if (target?.id) {
      this.credentialService.delete(target.id).subscribe({
        next: () => {
          this.credentials.update(list => list.filter(c => c.id !== target.id));
          this.showDeleteConfirm.set(false);
        }
      });
    }
  }

  onDeleteCancelled(): void {
    this.showDeleteConfirm.set(false);
    this.deleteTarget.set(null);
  }
}
