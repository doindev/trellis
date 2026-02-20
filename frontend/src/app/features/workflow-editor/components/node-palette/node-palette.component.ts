import { Component, Input, Output, EventEmitter, signal, computed, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  LucideAngularModule, LucideIconProvider, LUCIDE_ICONS,
  Globe, Merge, ArrowRight, Split, Clock, Play, Webhook, Reply,
  UnfoldVertical, Route, Pen, Code,
  CalendarClock, ListFilter, GitCompare, Lock, CopyMinus, FileText,
  Layers, Repeat, FileCode, ListEnd, ArrowUpNarrowWide, Replace, Sigma,
  Table2
} from 'lucide-angular';
import { NodeTypeDescription } from '../../../../core/models';

@Component({
  selector: 'app-node-palette',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
  providers: [{
    provide: LUCIDE_ICONS,
    multi: true,
    useValue: new LucideIconProvider({
      Globe, Merge, ArrowRight, Split, Clock, Play, Webhook, Reply,
      UnfoldVertical, Route, Pen, Code,
      CalendarClock, ListFilter, GitCompare, Lock, CopyMinus, FileText,
      Layers, Repeat, FileCode, ListEnd, ArrowUpNarrowWide, Replace, Sigma,
      Table2
    })
  }],
  templateUrl: './node-palette.component.html',
  styleUrl: './node-palette.component.scss'
})
export class NodePaletteComponent {
  @ViewChild('searchInput') searchInput!: ElementRef<HTMLInputElement>;

  @Input() set nodeTypes(value: Map<string, NodeTypeDescription[]>) {
    this._nodeTypes.set(value);
  }
  @Output() close = new EventEmitter<void>();
  @Output() nodeClicked = new EventEmitter<NodeTypeDescription>();

  private _nodeTypes = signal<Map<string, NodeTypeDescription[]>>(new Map());
  searchTerm = signal('');
  triggerOnly = signal(false);

  filteredTypes = computed(() => {
    const term = this.searchTerm().toLowerCase();
    const triggersOnly = this.triggerOnly();
    const types = this._nodeTypes();
    if (!term && !triggersOnly) return types;

    const filtered = new Map<string, NodeTypeDescription[]>();
    types.forEach((nodes, category) => {
      let matching = nodes;
      if (triggersOnly) {
        matching = matching.filter(n => n.isTrigger);
      }
      if (term) {
        matching = matching.filter(n =>
          n.displayName.toLowerCase().includes(term) ||
          n.type.toLowerCase().includes(term) ||
          n.description?.toLowerCase().includes(term)
        );
      }
      if (matching.length > 0) {
        filtered.set(category, matching);
      }
    });
    return filtered;
  });

  focusSearch(): void {
    requestAnimationFrame(() => {
      const el = this.searchInput?.nativeElement;
      if (el) {
        el.focus();
        if (el.value) {
          el.setSelectionRange(0, el.value.length);
        }
      }
    });
  }

  expandedCategories = new Set<string>();

  toggleCategory(category: string): void {
    if (this.expandedCategories.has(category)) {
      this.expandedCategories.delete(category);
    } else {
      this.expandedCategories.add(category);
    }
  }

  isCategoryExpanded(category: string): boolean {
    return this.expandedCategories.has(category) || this.searchTerm().length > 0 || this.triggerOnly();
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
