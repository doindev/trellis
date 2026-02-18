import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
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
export class ExecutionListComponent implements OnInit, OnDestroy {
  executions = signal<Execution[]>([]);
  loading = signal(true);
  statusFilter = signal('all');
  autoRefresh = signal(true);
  private refreshInterval: ReturnType<typeof setInterval> | null = null;
  private readonly REFRESH_MS = 5_000;

  filteredExecutions = computed(() => {
    const filter = this.statusFilter();
    const execs = this.executions();
    if (filter === 'all') return execs;
    return execs.filter(e => e.status === filter);
  });

  constructor(private executionService: ExecutionService) {}

  ngOnInit(): void {
    this.loadExecutions();
    this.startAutoRefresh();
  }

  ngOnDestroy(): void {
    this.stopAutoRefresh();
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

  private refreshExecutions(): void {
    this.executionService.list().subscribe({
      next: (data) => this.executions.set(data)
    });
  }

  onAutoRefreshChange(enabled: boolean): void {
    this.autoRefresh.set(enabled);
    if (enabled) {
      this.startAutoRefresh();
    } else {
      this.stopAutoRefresh();
    }
  }

  private startAutoRefresh(): void {
    this.stopAutoRefresh();
    if (this.autoRefresh()) {
      this.refreshInterval = setInterval(() => {
        this.refreshExecutions();
      }, this.REFRESH_MS);
    }
  }

  private stopAutoRefresh(): void {
    if (this.refreshInterval !== null) {
      clearInterval(this.refreshInterval);
      this.refreshInterval = null;
    }
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
