import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ExecutionService } from '../../core/services';
import { Execution } from '../../core/models';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-execution-list',
  standalone: true,
  imports: [CommonModule, FormsModule, LoadingSpinnerComponent],
  templateUrl: './execution-list.component.html',
  styleUrl: './execution-list.component.scss'
})
export class ExecutionListComponent implements OnInit {
  executions = signal<Execution[]>([]);
  loading = signal(true);
  statusFilter = signal('all');

  filteredExecutions = computed(() => {
    const filter = this.statusFilter();
    const execs = this.executions();
    if (filter === 'all') return execs;
    return execs.filter(e => e.status === filter);
  });

  constructor(private executionService: ExecutionService) {}

  ngOnInit(): void {
    this.loadExecutions();
  }

  loadExecutions(): void {
    this.loading.set(true);
    this.executionService.list().subscribe({
      next: (data) => {
        this.executions.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  deleteExecution(id: string, event: Event): void {
    event.stopPropagation();
    this.executionService.delete(id).subscribe({
      next: () => {
        this.executions.update(list => list.filter(e => e.id !== id));
      }
    });
  }

  retryExecution(id: string, event: Event): void {
    event.stopPropagation();
    this.executionService.retry(id).subscribe();
  }

  onFilterChange(status: string): void {
    this.statusFilter.set(status);
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'success': return 'bg-success';
      case 'error': return 'bg-danger';
      case 'running': return 'bg-warning';
      case 'waiting': return 'bg-secondary';
      default: return 'bg-secondary';
    }
  }
}
