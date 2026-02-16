import { Component, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { WorkflowService } from '../../core/services';
import { WorkflowEditorStore } from '../../core/state/workflow-editor.store';
import { NodeTypeStore } from '../../core/state/node-type.store';
import { ToolbarComponent } from './components/toolbar/toolbar.component';
import { NodePaletteComponent } from './components/node-palette/node-palette.component';
import { ReactFlowWrapperComponent } from './components/canvas/react-flow-wrapper.component';
import { ParameterPanelComponent } from './components/parameter-panel/parameter-panel.component';
import { EditorDrawerComponent } from './components/editor-drawer/editor-drawer.component';

@Component({
  selector: 'app-workflow-editor',
  standalone: true,
  imports: [
    CommonModule,
    ToolbarComponent,
    NodePaletteComponent,
    ReactFlowWrapperComponent,
    ParameterPanelComponent,
    EditorDrawerComponent
  ],
  templateUrl: './workflow-editor.component.html',
  styleUrl: './workflow-editor.component.scss'
})
export class WorkflowEditorComponent implements OnInit {
  showPalette = false;
  drawerExpanded = false;
  activeTab: 'editor' | 'executions' = 'editor';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private workflowService: WorkflowService,
    public store: WorkflowEditorStore,
    public nodeTypeStore: NodeTypeStore
  ) {}

  ngOnInit(): void {
    this.nodeTypeStore.loadNodeTypes();

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.workflowService.get(id).subscribe({
        next: (workflow) => this.store.loadWorkflow(workflow),
        error: () => this.router.navigate(['/home/workflows'])
      });
    } else {
      this.store.createNew();
    }
  }

  togglePalette(): void {
    this.showPalette = !this.showPalette;
  }

  toggleDrawer(): void {
    this.drawerExpanded = !this.drawerExpanded;
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Tab' && this.activeTab === 'editor') {
      const target = event.target as HTMLElement;
      const isInput = target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT' || target.isContentEditable;
      if (!isInput) {
        event.preventDefault();
        this.togglePalette();
      }
    }

    if ((event.ctrlKey || event.metaKey) && event.key === 'j') {
      event.preventDefault();
      this.toggleDrawer();
    }
  }

  onSave(): void {
    this.store.saveWorkflow();
  }

  onExecute(): void {
    const wf = this.store.workflow();
    if (!wf?.id) {
      this.store.saveWorkflow();
      return;
    }
    this.store.setIsExecuting(true);
    this.drawerExpanded = true;
    this.workflowService.run(wf.id).subscribe({
      next: (result) => {
        this.store.setExecutionData(result);
        this.store.setIsExecuting(false);
      },
      error: () => {
        this.store.setIsExecuting(false);
      }
    });
  }

  onTabChanged(tab: 'editor' | 'executions'): void {
    this.activeTab = tab;
    if (tab === 'executions') {
      this.drawerExpanded = true;
    }
  }

  goBack(): void {
    this.router.navigate(['/home/workflows']);
  }
}
