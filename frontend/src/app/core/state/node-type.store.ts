import { Injectable, signal, computed } from '@angular/core';
import { NodeTypeDescription } from '../models';
import { NodeTypeService } from '../services';

@Injectable({ providedIn: 'root' })
export class NodeTypeStore {
  readonly nodeTypes = signal<Map<string, NodeTypeDescription>>(new Map());
  readonly loading = signal(false);
  readonly loaded = signal(false);

  readonly categories = computed(() => {
    const types = this.nodeTypes();
    const cats = new Set<string>();
    types.forEach(t => cats.add(t.category));
    return [...cats].sort();
  });

  readonly nodeTypesList = computed(() => {
    return [...this.nodeTypes().values()];
  });

  readonly groupedByCategory = computed(() => {
    const types = this.nodeTypesList();
    const grouped = new Map<string, NodeTypeDescription[]>();
    types.forEach(t => {
      const list = grouped.get(t.category) || [];
      list.push(t);
      grouped.set(t.category, list);
    });
    return grouped;
  });

  constructor(private nodeTypeService: NodeTypeService) {}

  loadNodeTypes(): void {
    if (this.loaded()) return;
    this.loading.set(true);
    this.nodeTypeService.getAll().subscribe({
      next: (types) => {
        const map = new Map<string, NodeTypeDescription>();
        types.forEach(t => map.set(t.type, t));
        this.nodeTypes.set(map);
        this.loading.set(false);
        this.loaded.set(true);
      },
      error: (err) => {
        console.error('Failed to load node types:', err);
        this.loading.set(false);
      }
    });
  }

  getByType(type: string): NodeTypeDescription | undefined {
    return this.nodeTypes().get(type);
  }
}
