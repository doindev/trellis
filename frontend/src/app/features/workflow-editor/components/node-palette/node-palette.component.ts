import { Component, Input, Output, EventEmitter, signal, computed, ViewChild, ElementRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, LucideIconProvider, LUCIDE_ICONS } from 'lucide-angular';
import { NODE_ICON_SET } from '../../../../shared/node-icons';
import { NodeTypeDescription, NodeParameter, ParameterOption } from '../../../../core/models';

export interface NodeClickedWithAction {
  nodeType: NodeTypeDescription;
  initialParams: Record<string, any>;
}

interface ActionGroup {
  label: string;
  items: ActionItem[];
}

interface ActionItem {
  action: string;
  description?: string;
  params: Record<string, any>;
}

interface ActionPanel {
  nodeType: NodeTypeDescription;
  groups: ActionGroup[];
}

@Component({
  selector: 'app-node-palette',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
  providers: [{
    provide: LUCIDE_ICONS,
    multi: true,
    useValue: new LucideIconProvider(NODE_ICON_SET)
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
  /** When set, only show nodes whose outputs include this AI connection type. */
  aiOutputTypeFilter = signal<string | null>(null);
  actionPanel = signal<ActionPanel | null>(null);
  private mouseX = 0;
  private mouseY = 0;

  @HostListener('mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    this.mouseX = event.clientX;
    this.mouseY = event.clientY;
  }

  @HostListener('keydown', ['$event'])
  onArrowKey(event: KeyboardEvent): void {
    if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return;
    const el = document.elementFromPoint(this.mouseX, this.mouseY);
    if (!el) return;
    const scrollable = this.findScrollableParent(el);
    if (!scrollable) return;
    event.preventDefault();
    scrollable.scrollBy({ top: event.key === 'ArrowDown' ? 60 : -60 });
  }

  private findScrollableParent(el: Element): Element | null {
    let current: Element | null = el;
    while (current) {
      if (current.scrollHeight > current.clientHeight) return current;
      current = current.parentElement;
    }
    return null;
  }

  /** Human-readable labels for AI connection types. */
  private static readonly AI_TYPE_LABELS: Record<string, string> = {
    ai_languageModel: 'Chat Models',
    ai_memory: 'Memory',
    ai_tool: 'Tools',
    ai_outputParser: 'Output Parsers',
    ai_embedding: 'Embeddings',
    ai_vectorStore: 'Vector Stores',
    ai_retriever: 'Retrievers',
    ai_agent: 'Agents',
    ai_document: 'Documents',
    ai_textSplitter: 'Text Splitters',
  };

  get aiFilterLabel(): string {
    const t = this.aiOutputTypeFilter();
    return t ? (NodePaletteComponent.AI_TYPE_LABELS[t] || t) : '';
  }

  filteredTypes = computed(() => {
    const term = this.searchTerm().toLowerCase();
    const triggersOnly = this.triggerOnly();
    const aiFilter = this.aiOutputTypeFilter();
    const types = this._nodeTypes();
    const isNodeTypeFilter = triggersOnly || !!aiFilter;
    const isSearching = term.length > 0;

    const filtered = new Map<string, NodeTypeDescription[]>();

    types.forEach((nodes, category) => {
      let matching = nodes;

      // Default view: hide searchOnly nodes
      if (!isSearching && !isNodeTypeFilter) {
        matching = matching.filter(n => !n.searchOnly);
      }

      // Apply node-type filters
      if (triggersOnly) {
        matching = matching.filter(n => n.isTrigger);
      }
      if (aiFilter) {
        matching = matching.filter(n =>
          n.outputs?.some(o => o.type === aiFilter)
        );
      }

      // Apply text search
      if (isSearching) {
        matching = matching.filter(n =>
          n.displayName.toLowerCase().includes(term) ||
          n.type.toLowerCase().includes(term) ||
          n.description?.toLowerCase().includes(term)
        );
      }

      if (matching.length === 0) return;

      // When a node-type filter is active, move other=true nodes to "Other"
      if (isNodeTypeFilter) {
        const normal = matching.filter(n => !n.other);
        const otherNodes = matching.filter(n => n.other);
        if (normal.length > 0) {
          filtered.set(category, normal);
        }
        if (otherNodes.length > 0) {
          const existing = filtered.get('Other') || [];
          existing.push(...otherNodes);
          filtered.set('Other', existing);
        }
      } else {
        // Text search or default: use real category
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
  collapsedCategories = new Set<string>();

  toggleCategory(category: string): void {
    if (this.isCategoryExpanded(category)) {
      this.expandedCategories.delete(category);
      this.collapsedCategories.add(category);
    } else {
      this.expandedCategories.add(category);
      this.collapsedCategories.delete(category);
    }
  }

  isCategoryExpanded(category: string): boolean {
    const autoExpand = this.searchTerm().length > 0 || this.triggerOnly() || !!this.aiOutputTypeFilter();
    if (autoExpand) {
      return !this.collapsedCategories.has(category);
    }
    return this.expandedCategories.has(category);
  }

  /** Check if a node has any action-bearing parameters (for showing the chevron). */
  hasActions(nodeType: NodeTypeDescription): boolean {
    if (!nodeType.parameters) return false;
    return nodeType.parameters.some(p =>
      p.type === 'options' && p.options?.some(o => o.action)
    );
  }

  /** Build grouped action panel data for a node type. */
  private buildActionPanel(nodeType: NodeTypeDescription): ActionPanel | null {
    if (!nodeType.parameters) return null;

    // Detect resource/operation pattern: a "resource" parameter whose displayOptions
    // control which operation parameters are shown
    const resourceParam = nodeType.parameters.find(p =>
      p.type === 'options' && p.name === 'resource'
    );

    if (resourceParam?.options?.length) {
      // Grouped pattern: each resource value maps to an operation parameter
      const groups: ActionGroup[] = [];

      for (const resOpt of resourceParam.options) {
        // Find operation parameters that are shown when this resource is selected
        const opParams = nodeType.parameters.filter(p =>
          p.type === 'options' && p.name !== 'resource' &&
          p.options?.some(o => o.action) &&
          this.isShownForResource(p, resourceParam.name, resOpt.value)
        );

        for (const opParam of opParams) {
          const items: ActionItem[] = opParam.options
            .filter(o => o.action)
            .map(o => ({
              action: o.action!,
              description: o.description,
              params: { [resourceParam.name]: resOpt.value, [opParam.name]: o.value }
            }))
            .sort((a, b) => a.action.localeCompare(b.action));

          if (items.length > 0) {
            groups.push({ label: resOpt.name + ' Actions', items });
          }
        }
      }

      if (groups.length > 0) {
        return { nodeType, groups };
      }
    }

    // Flat pattern: single operation/action parameter with action fields
    const actionParam = nodeType.parameters.find(p =>
      p.type === 'options' && (p.name === 'operation' || p.name === 'action') &&
      p.options?.some(o => o.action)
    );

    if (actionParam) {
      const items: ActionItem[] = actionParam.options
        .filter(o => o.action)
        .map(o => ({
          action: o.action!,
          description: o.description,
          params: { [actionParam.name]: o.value }
        }))
        .sort((a, b) => a.action.localeCompare(b.action));

      if (items.length > 0) {
        return { nodeType, groups: [{ label: '', items }] };
      }
    }

    return null;
  }

  /** Check if a parameter's displayOptions show it for a given resource value. */
  private isShownForResource(param: NodeParameter, resourceName: string, resourceValue: any): boolean {
    const show = param.displayOptions?.show;
    if (!show) return true;
    const allowed = show[resourceName];
    if (!allowed) return true;
    return Array.isArray(allowed) && allowed.includes(resourceValue);
  }

  onNodeItemClick(nodeType: NodeTypeDescription): void {
    const panel = this.buildActionPanel(nodeType);
    if (panel) {
      this.actionPanel.set(panel);
    } else {
      this.nodeClicked.emit(nodeType);
    }
  }

  onActionSelected(item: ActionItem): void {
    const panel = this.actionPanel();
    if (!panel) return;
    this.actionPanel.set(null);
    this.nodeClickedWithAction.emit({
      nodeType: panel.nodeType,
      initialParams: item.params
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

  clearSearch(): void {
    this.searchTerm.set('');
    this.actionPanel.set(null);
    this.focusSearch();
  }

  getCategoryEntries(): [string, NodeTypeDescription[]][] {
    return Array.from(this.filteredTypes().entries())
      .sort((a, b) => {
        if (a[0] === 'Other') return 1;
        if (b[0] === 'Other') return -1;
        return a[0].localeCompare(b[0]);
      })
      .map(([cat, nodes]) => [
        cat,
        [...nodes].sort((a, b) => a.displayName.localeCompare(b.displayName))
      ]);
  }
}
