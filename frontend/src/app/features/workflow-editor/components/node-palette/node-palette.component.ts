import { Component, Input, Output, EventEmitter, signal, computed, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  LucideAngularModule, LucideIconProvider, LUCIDE_ICONS,
  Globe, Merge, ArrowRight, Split, Clock, Play, Webhook, Reply,
  UnfoldVertical, Route, Pen, Code,
  CalendarClock, ListFilter, GitCompare, Lock, CopyMinus, FileText,
  Layers, Repeat, FileCode, ListEnd, ArrowUpNarrowWide, Replace, Sigma,
  Table2, Timer, ClipboardList, FileInput
} from 'lucide-angular';
import { NodeTypeDescription, ParameterOption } from '../../../../core/models';

export interface NodeClickedWithAction {
  nodeType: NodeTypeDescription;
  paramName: string;
  paramValue: any;
}

interface ActionPanel {
  nodeType: NodeTypeDescription;
  paramName: string;
  options: ParameterOption[];
}

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
      Table2, Timer, ClipboardList, FileInput
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
  @Output() nodeClickedWithAction = new EventEmitter<NodeClickedWithAction>();

  private _nodeTypes = signal<Map<string, NodeTypeDescription[]>>(new Map());
  searchTerm = signal('');
  triggerOnly = signal(false);
  actionPanel = signal<ActionPanel | null>(null);

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

  getActionOptions(nodeType: NodeTypeDescription): { paramName: string; options: ParameterOption[] } | null {
    if (!nodeType.parameters) return null;
    const param = nodeType.parameters.find(p =>
      p.type === 'options' && (p.name === 'operation' || p.name === 'action') &&
      p.options?.some(o => o.action)
    );
    if (!param) return null;
    return { paramName: param.name, options: param.options.filter(o => o.action) };
  }

  onNodeItemClick(nodeType: NodeTypeDescription): void {
    const actions = this.getActionOptions(nodeType);
    if (actions && actions.options.length > 0) {
      this.actionPanel.set({
        nodeType,
        paramName: actions.paramName,
        options: actions.options
      });
    } else {
      this.nodeClicked.emit(nodeType);
    }
  }

  onActionSelected(value: any): void {
    const panel = this.actionPanel();
    if (!panel) return;
    this.actionPanel.set(null);
    this.nodeClickedWithAction.emit({
      nodeType: panel.nodeType,
      paramName: panel.paramName,
      paramValue: value
    });
  }

  closeActionPanel(): void {
    this.actionPanel.set(null);
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
    this.actionPanel.set(null);
  }

  getCategoryEntries(): [string, NodeTypeDescription[]][] {
    return Array.from(this.filteredTypes().entries());
  }
}
