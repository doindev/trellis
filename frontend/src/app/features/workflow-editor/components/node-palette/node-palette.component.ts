import { Component, Input, Output, EventEmitter, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeTypeDescription } from '../../../../core/models';

@Component({
  selector: 'app-node-palette',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './node-palette.component.html',
  styleUrl: './node-palette.component.scss'
})
export class NodePaletteComponent {
  @Input() set nodeTypes(value: Map<string, NodeTypeDescription[]>) {
    this._nodeTypes.set(value);
  }
  @Output() close = new EventEmitter<void>();

  private _nodeTypes = signal<Map<string, NodeTypeDescription[]>>(new Map());
  searchTerm = signal('');

  filteredTypes = computed(() => {
    const term = this.searchTerm().toLowerCase();
    const types = this._nodeTypes();
    if (!term) return types;

    const filtered = new Map<string, NodeTypeDescription[]>();
    types.forEach((nodes, category) => {
      const matching = nodes.filter(n =>
        n.displayName.toLowerCase().includes(term) ||
        n.type.toLowerCase().includes(term) ||
        n.description?.toLowerCase().includes(term)
      );
      if (matching.length > 0) {
        filtered.set(category, matching);
      }
    });
    return filtered;
  });

  expandedCategories = new Set<string>();

  toggleCategory(category: string): void {
    if (this.expandedCategories.has(category)) {
      this.expandedCategories.delete(category);
    } else {
      this.expandedCategories.add(category);
    }
  }

  isCategoryExpanded(category: string): boolean {
    return this.expandedCategories.has(category) || this.searchTerm().length > 0;
  }

  onDragStart(event: DragEvent, nodeType: NodeTypeDescription): void {
    if (event.dataTransfer) {
      event.dataTransfer.setData('application/trellis-node-type', JSON.stringify({
        type: nodeType.type,
        displayName: nodeType.displayName,
        version: nodeType.version
      }));
      event.dataTransfer.effectAllowed = 'move';
    }
  }

  onSearchChange(value: string): void {
    this.searchTerm.set(value);
  }

  getCategoryEntries(): [string, NodeTypeDescription[]][] {
    return Array.from(this.filteredTypes().entries());
  }
}
