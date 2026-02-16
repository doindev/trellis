import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CredentialService } from '../../core/services';
import { Credential, CredentialSchema } from '../../core/models';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-credential-list',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent, LoadingSpinnerComponent],
  templateUrl: './credential-list.component.html',
  styleUrl: './credential-list.component.scss'
})
export class CredentialListComponent implements OnInit {
  credentials = signal<Credential[]>([]);
  loading = signal(true);
  showModal = signal(false);
  editingCredential = signal<Partial<Credential>>({});
  schema = signal<CredentialSchema | null>(null);
  isEditing = signal(false);
  showDeleteConfirm = signal(false);
  deleteTarget = signal<Credential | null>(null);

  constructor(private credentialService: CredentialService) {}

  ngOnInit(): void {
    this.loadCredentials();
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

  openCreateModal(): void {
    this.editingCredential.set({ name: '', type: '', data: {} });
    this.schema.set(null);
    this.isEditing.set(false);
    this.showModal.set(true);
  }

  openEditModal(cred: Credential): void {
    this.editingCredential.set({ ...cred });
    this.isEditing.set(true);
    this.showModal.set(true);
    if (cred.type) {
      this.loadSchema(cred.type);
    }
  }

  loadSchema(type: string): void {
    this.credentialService.getSchema(type).subscribe({
      next: (s) => this.schema.set(s),
      error: () => this.schema.set(null)
    });
  }

  onTypeChange(type: string): void {
    const cred = this.editingCredential();
    this.editingCredential.set({ ...cred, type });
    if (type) {
      this.loadSchema(type);
    } else {
      this.schema.set(null);
    }
  }

  onDataFieldChange(fieldName: string, value: string): void {
    const cred = this.editingCredential();
    const data = { ...(cred.data || {}), [fieldName]: value };
    this.editingCredential.set({ ...cred, data });
  }

  saveCredential(): void {
    const cred = this.editingCredential();
    if (!cred.name || !cred.type) return;

    const operation = this.isEditing() && cred.id
      ? this.credentialService.update(cred.id, cred)
      : this.credentialService.create(cred);

    operation.subscribe({
      next: () => {
        this.showModal.set(false);
        this.loadCredentials();
      }
    });
  }

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

  updateCredentialField(field: string, value: string): void {
    const cred = this.editingCredential();
    this.editingCredential.set(Object.assign({}, cred, { [field]: value }));
  }

  closeModal(): void {
    this.showModal.set(false);
  }
}
