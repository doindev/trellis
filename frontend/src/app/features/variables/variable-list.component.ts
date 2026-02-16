import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { VariableService } from '../../core/services';
import { Variable } from '../../core/models';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-variable-list',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent, LoadingSpinnerComponent],
  templateUrl: './variable-list.component.html',
  styleUrl: './variable-list.component.scss'
})
export class VariableListComponent implements OnInit {
  variables = signal<Variable[]>([]);
  loading = signal(true);
  showModal = signal(false);
  editingVariable = signal<Partial<Variable>>({});
  isEditing = signal(false);
  showDeleteConfirm = signal(false);
  deleteTarget = signal<Variable | null>(null);

  constructor(private variableService: VariableService) {}

  ngOnInit(): void {
    this.loadVariables();
  }

  loadVariables(): void {
    this.loading.set(true);
    this.variableService.list().subscribe({
      next: (data) => {
        this.variables.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openCreate(): void {
    this.editingVariable.set({ key: '', value: '', type: 'string' });
    this.isEditing.set(false);
    this.showModal.set(true);
  }

  openEdit(variable: Variable): void {
    this.editingVariable.set({ ...variable });
    this.isEditing.set(true);
    this.showModal.set(true);
  }

  saveVariable(): void {
    const v = this.editingVariable();
    if (!v.key || !v.value) return;

    const op = this.isEditing() && v.id
      ? this.variableService.update(v.id, v)
      : this.variableService.create(v);

    op.subscribe({
      next: () => {
        this.showModal.set(false);
        this.loadVariables();
      }
    });
  }

  confirmDelete(variable: Variable, event: Event): void {
    event.stopPropagation();
    this.deleteTarget.set(variable);
    this.showDeleteConfirm.set(true);
  }

  onDeleteConfirmed(): void {
    const target = this.deleteTarget();
    if (target?.id) {
      this.variableService.delete(target.id).subscribe({
        next: () => {
          this.variables.update(list => list.filter(v => v.id !== target.id));
          this.showDeleteConfirm.set(false);
        }
      });
    }
  }

  onDeleteCancelled(): void {
    this.showDeleteConfirm.set(false);
    this.deleteTarget.set(null);
  }

  updateField(field: string, value: string): void {
    const v = this.editingVariable();
    this.editingVariable.set(Object.assign({}, v, { [field]: value }));
  }

  closeModal(): void {
    this.showModal.set(false);
  }
}
