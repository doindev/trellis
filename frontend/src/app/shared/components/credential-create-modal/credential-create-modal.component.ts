import { Component, ElementRef, EventEmitter, Input, OnInit, Output, ViewChild, signal, computed } from '@angular/core';
import { CommonModule, KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CredentialService } from '../../../core/services';
import { ProjectService } from '../../../core/services/project.service';
import { Credential, CredentialProperty, CredentialSchema } from '../../../core/models';
import { Project } from '../../../core/models/project.model';

@Component({
  selector: 'app-credential-create-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, KeyValuePipe],
  templateUrl: './credential-create-modal.component.html',
  styleUrl: './credential-create-modal.component.scss'
})
export class CredentialCreateModalComponent implements OnInit {
  @Input() projectId?: string;
  @Output() saved = new EventEmitter<Credential>();
  @Output() closed = new EventEmitter<void>();
  @ViewChild('typeSearchInput') typeSearchInput!: ElementRef<HTMLInputElement>;
  @ViewChild('editorMainArea') editorMainArea!: ElementRef<HTMLDivElement>;

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
      (t.description && t.description.toLowerCase().includes(term)) ||
      (t.category && t.category.toLowerCase().includes(term))
    );
  });

  groupedFilteredTypes = computed(() => {
    const types = this.filteredTypes();
    const groups = new Map<string, CredentialSchema[]>();
    for (const t of types) {
      const cat = t.category || 'Generic';
      if (!groups.has(cat)) {
        groups.set(cat, []);
      }
      groups.get(cat)!.push(t);
    }
    return groups;
  });

  // Modal 2: Credential editor
  showEditorModal = signal(false);
  editorCredential = signal<Partial<Credential>>({});
  editorSchema = signal<CredentialSchema | null>(null);
  editorTab = signal<'connection' | 'sharing' | 'details'>('connection');
  isEditing = signal(false);
  saving = signal(false);
  validating = signal(false);
  validationError = signal<string | null>(null);
  formDisabled = computed(() => this.validating() || this.saving());

  // Sharing state
  allProjects = signal<Project[]>([]);
  sharedWithProjectIds = signal<string[]>([]);
  selectedShareProjectId = signal<string>('');

  /** Whether the current credential is owned by the current project */
  get isOwnedByCurrentProject(): boolean {
    const cred = this.editorCredential();
    return !!this.projectId && cred.projectId === this.projectId;
  }

  /** Projects available to share with (excludes current project and already-shared projects) */
  shareableProjects = computed(() => {
    const currentId = this.projectId;
    const shared = new Set(this.sharedWithProjectIds());
    return this.allProjects().filter(p => p.id !== currentId && !shared.has(p.id!));
  });

  /** Projects currently shared with (resolved names) */
  sharedProjects = computed(() => {
    const ids = new Set(this.sharedWithProjectIds());
    return this.allProjects().filter(p => ids.has(p.id!));
  });

  constructor(private credentialService: CredentialService, private projectService: ProjectService) {}

  ngOnInit(): void {
    this.loadTypes();
  }

  loadTypes(): void {
    this.credentialService.listTypes().subscribe({
      next: (types) => this.credentialTypes.set(types),
      error: () => {}
    });
  }

  // --- Public API ---

  openCreate(): void {
    this.typeSearchTerm.set('');
    this.selectedType.set(null);
    this.showTypeSelectModal.set(true);
    setTimeout(() => this.typeSearchInput?.nativeElement?.focus(), 50);
  }

  /**
   * Opens the credential editor directly for a known type, skipping type selection.
   * Falls back to openCreate() if the type is not found.
   */
  openCreateForType(typeName: string): void {
    const type = this.credentialTypes().find(t => t.type === typeName);
    if (!type) {
      this.openCreate();
      return;
    }

    const data: Record<string, any> = {};
    for (const prop of type.properties) {
      if (prop.defaultValue != null) {
        data[prop.name] = prop.defaultValue;
      }
    }

    this.editorCredential.set({
      name: type.displayName + ' account',
      type: type.type,
      projectId: this.projectId,
      data
    });
    this.editorSchema.set(type);
    this.editorTab.set('connection');
    this.isEditing.set(false);
    this.validationError.set(null);
    this.showEditorModal.set(true);
    this.focusFirstEditorInput();
  }

  openEdit(cred: Credential): void {
    this.editorCredential.set({ ...cred });
    this.isEditing.set(true);
    this.editorTab.set('connection');
    this.validationError.set(null);
    this.editorSchema.set(null);
    this.showEditorModal.set(true);
    this.focusFirstEditorInput();
    this.loadShareData();

    if (cred.type) {
      this.credentialService.getSchema(cred.type).subscribe({
        next: (s) => {
          this.editorSchema.set(s);
          this.focusFirstEditorInput();
        },
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

  // --- Modal 1: Type selection ---

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
      projectId: this.projectId,
      data
    });
    this.editorSchema.set(type);
    this.editorTab.set('connection');
    this.isEditing.set(false);
    this.validationError.set(null);
    this.showEditorModal.set(true);
    this.focusFirstEditorInput();
  }

  closeTypeSelectModal(): void {
    this.showTypeSelectModal.set(false);
    this.closed.emit();
  }

  // --- Modal 2: Credential editor ---

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

    this.validationError.set(null);
    this.validating.set(true);

    this.credentialService.testCredentials(cred.type, cred.data || {}).subscribe({
      next: (testResult) => {
        if (!testResult.success) {
          this.validationError.set(testResult.error || 'Credential validation failed');
          this.validating.set(false);
          return;
        }
        this.validating.set(false);
        this.saving.set(true);
        this.persistCredential(cred);
      },
      error: (err) => {
        this.validationError.set('Could not validate credentials. Please try again.');
        this.validating.set(false);
      }
    });
  }

  private persistCredential(cred: Partial<Credential>): void {
    const operation = this.isEditing() && cred.id
      ? this.credentialService.update(cred.id, cred)
      : this.credentialService.create(cred);

    operation.subscribe({
      next: (result) => {
        this.saving.set(false);
        this.showEditorModal.set(false);
        this.saved.emit(result);
      },
      error: () => this.saving.set(false)
    });
  }

  closeEditorModal(): void {
    this.showEditorModal.set(false);
    this.closed.emit();
  }

  isPasswordField(prop: CredentialProperty): boolean {
    return prop.typeOptions?.['password'] === true || prop.type === 'password';
  }

  isPropertyVisible(prop: CredentialProperty): boolean {
    if (!prop.displayOptions?.show) return true;
    const data = this.editorCredential().data || {};
    for (const [field, allowedValues] of Object.entries(prop.displayOptions.show)) {
      const currentValue = data[field];
      if (!allowedValues.includes(currentValue)) {
        return false;
      }
    }
    return true;
  }

  // --- Expression toggle ---

  isExpressionValue(fieldName: string): boolean {
    const val = this.editorCredential().data?.[fieldName];
    return typeof val === 'string' && val.startsWith('={{');
  }

  setExpressionMode(fieldName: string): void {
    if (!this.isExpressionValue(fieldName)) {
      this.onDataFieldChange(fieldName, '={{ }}');
    }
  }

  setFixedMode(fieldName: string, prop: CredentialProperty): void {
    if (this.isExpressionValue(fieldName)) {
      const defaultVal = prop.type === 'boolean' ? (prop.defaultValue ?? false)
        : prop.type === 'options' ? (prop.defaultValue ?? '')
        : (prop.defaultValue ?? '');
      this.onDataFieldChange(fieldName, defaultVal);
    }
  }

  private focusFirstEditorInput(): void {
    // Wait for Angular to render the editor fields, then focus the first enabled input/select
    setTimeout(() => {
      const container = this.editorMainArea?.nativeElement;
      if (!container) return;
      const firstInput = container.querySelector<HTMLElement>(
        'input:not([disabled]):not([type="hidden"]):not([type="checkbox"]), select:not([disabled])'
      );
      firstInput?.focus();
    }, 100);
  }

  // --- Sharing ---

  loadShareData(): void {
    this.projectService.list().subscribe({
      next: (projects) => this.allProjects.set(projects),
      error: () => {}
    });
    const credId = this.editorCredential().id;
    if (credId) {
      this.credentialService.getShares(credId).subscribe({
        next: (ids) => this.sharedWithProjectIds.set(ids),
        error: () => this.sharedWithProjectIds.set([])
      });
    } else {
      this.sharedWithProjectIds.set([]);
    }
  }

  addShare(): void {
    const targetId = this.selectedShareProjectId();
    const credId = this.editorCredential().id;
    if (!targetId || !credId || !this.projectId) return;

    this.credentialService.shareCredential(credId, targetId, this.projectId).subscribe({
      next: () => {
        this.sharedWithProjectIds.set([...this.sharedWithProjectIds(), targetId]);
        this.selectedShareProjectId.set('');
      },
      error: () => {}
    });
  }

  removeShare(targetProjectId: string): void {
    const credId = this.editorCredential().id;
    if (!credId) return;

    this.credentialService.unshareCredential(credId, targetProjectId).subscribe({
      next: () => {
        this.sharedWithProjectIds.set(
          this.sharedWithProjectIds().filter(id => id !== targetProjectId)
        );
      },
      error: () => {}
    });
  }

  // Keep original insertion order for KeyValuePipe
  keepOrder = () => 0;
}
