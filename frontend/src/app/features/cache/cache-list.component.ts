import { Component, ElementRef, OnInit, ViewChild, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CacheService, CacheDefinition } from '../../core/services/cache.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-cache-list',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent],
  templateUrl: './cache-list.component.html',
  styleUrl: './cache-list.component.scss'
})
export class CacheListComponent implements OnInit {
  @ViewChild('nameInput') nameInput!: ElementRef<HTMLInputElement>;
  caches = signal<CacheDefinition[]>([]);
  loading = signal(true);
  showModal = signal(false);
  editingCache = signal<Partial<CacheDefinition>>({});
  isEditing = signal(false);
  showDeleteConfirm = signal(false);
  deleteTarget = signal<CacheDefinition | null>(null);

  constructor(private cacheService: CacheService) {}

  ngOnInit(): void {
    this.loadCaches();
  }

  loadCaches(): void {
    this.loading.set(true);
    this.cacheService.list().subscribe({
      next: (data) => {
        this.caches.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openCreate(): void {
    this.editingCache.set({ name: '', description: '', maxSize: 1000, ttlSeconds: 3600 });
    this.isEditing.set(false);
    this.showModal.set(true);
    this.focusNameInput();
  }

  openEdit(cache: CacheDefinition): void {
    this.editingCache.set({ ...cache });
    this.isEditing.set(true);
    this.showModal.set(true);
    this.focusNameInput();
  }

  private focusNameInput(): void {
    setTimeout(() => this.nameInput?.nativeElement?.focus(), 50);
  }

  saveCache(): void {
    const c = this.editingCache();
    if (!c.name) return;

    const op = this.isEditing() && c.id
      ? this.cacheService.update(c.id, c)
      : this.cacheService.create(c);

    op.subscribe({
      next: () => {
        this.showModal.set(false);
        this.loadCaches();
      }
    });
  }

  clearCache(cache: CacheDefinition, event: Event): void {
    event.stopPropagation();
    this.cacheService.clear(cache.id).subscribe({
      next: () => this.loadCaches()
    });
  }

  confirmDelete(cache: CacheDefinition, event: Event): void {
    event.stopPropagation();
    this.deleteTarget.set(cache);
    this.showDeleteConfirm.set(true);
  }

  onDeleteConfirmed(): void {
    const target = this.deleteTarget();
    if (target?.id) {
      this.cacheService.delete(target.id).subscribe({
        next: () => {
          this.caches.update(list => list.filter(c => c.id !== target.id));
          this.showDeleteConfirm.set(false);
        }
      });
    }
  }

  onDeleteCancelled(): void {
    this.showDeleteConfirm.set(false);
    this.deleteTarget.set(null);
  }

  updateField(field: string, value: any): void {
    const c = this.editingCache();
    this.editingCache.set(Object.assign({}, c, { [field]: value }));
  }

  closeModal(): void {
    this.showModal.set(false);
  }

  formatTtl(seconds: number): string {
    if (seconds < 60) return `${seconds}s`;
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)}h`;
    return `${Math.floor(seconds / 86400)}d`;
  }

  formatHitRate(rate?: number): string {
    if (rate == null) return '-';
    return `${Math.round(rate * 100)}%`;
  }
}
