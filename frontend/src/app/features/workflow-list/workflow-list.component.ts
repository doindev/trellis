import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { WorkflowService } from '../../core/services';
import { Workflow } from '../../core/models';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-workflow-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, ConfirmDialogComponent, LoadingSpinnerComponent],
  templateUrl: './workflow-list.component.html',
  styleUrl: './workflow-list.component.scss'
})
export class WorkflowListComponent implements OnInit {
  workflows = signal<Workflow[]>([]);
  loading = signal(true);
  searchTerm = signal('');
  statusFilter = signal('');
  sortBy = signal('updatedAt');
  deleteTarget = signal<Workflow | null>(null);
  showDeleteConfirm = signal(false);

  filteredWorkflows = computed(() => {
    const term = this.searchTerm().toLowerCase();
    const status = this.statusFilter();
    const sort = this.sortBy();
    let wfs = this.workflows();

    if (term) {
      wfs = wfs.filter(w => w.name.toLowerCase().includes(term));
    }

    if (status === 'published') {
      wfs = wfs.filter(w => w.published);
    } else if (status === 'unpublished') {
      wfs = wfs.filter(w => !w.published);
    }

    wfs = [...wfs].sort((a, b) => {
      if (sort === 'name') {
        return a.name.localeCompare(b.name);
      } else if (sort === 'createdAt') {
        return new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime();
      }
      return new Date(b.updatedAt || 0).getTime() - new Date(a.updatedAt || 0).getTime();
    });

    return wfs;
  });

  constructor(
    private workflowService: WorkflowService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadWorkflows();
  }

  loadWorkflows(): void {
    this.loading.set(true);
    this.workflowService.list().subscribe({
      next: (data) => {
        this.workflows.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  createWorkflow(): void {
    this.router.navigate(['/workflow/new']);
  }

  openWorkflow(workflow: Workflow): void {
    if (workflow.id) {
      this.router.navigate(['/workflow', workflow.id]);
    }
  }

  confirmDelete(workflow: Workflow, event: Event): void {
    event.stopPropagation();
    this.deleteTarget.set(workflow);
    this.showDeleteConfirm.set(true);
  }

  onDeleteConfirmed(): void {
    const target = this.deleteTarget();
    if (target?.id) {
      this.workflowService.delete(target.id).subscribe({
        next: () => {
          this.workflows.update(wfs => wfs.filter(w => w.id !== target.id));
          this.showDeleteConfirm.set(false);
          this.deleteTarget.set(null);
        }
      });
    }
  }

  onDeleteCancelled(): void {
    this.showDeleteConfirm.set(false);
    this.deleteTarget.set(null);
  }

  onSearchChange(value: string): void {
    this.searchTerm.set(value);
  }

  onStatusFilterChange(value: string): void {
    this.statusFilter.set(value);
  }

  onSortChange(value: string): void {
    this.sortBy.set(value);
  }
}
