import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { WorkflowService } from '../../core/services';
import { WorkflowEditorStore } from '../../core/state/workflow-editor.store';
import { NodeTypeStore } from '../../core/state/node-type.store';
import { ToolbarComponent } from './components/toolbar/toolbar.component';
import { NodePaletteComponent } from './components/node-palette/node-palette.component';
import { ReactFlowWrapperComponent } from './components/canvas/react-flow-wrapper.component';
import { ParameterPanelComponent } from './components/parameter-panel/parameter-panel.component';
import { ExecutionPanelComponent } from './components/execution-panel/execution-panel.component';

@Component({
  selector: 'app-workflow-editor',
  standalone: true,
  imports: [
    CommonModule,
    ToolbarComponent,
    NodePaletteComponent,
    ReactFlowWrapperComponent,
    ParameterPanelComponent,
    ExecutionPanelComponent
  ],
  templateUrl: './workflow-editor.component.html',
  styleUrl: './workflow-editor.component.scss'
})
export class WorkflowEditorComponent implements OnInit {
  showPalette = true;
  showExecutionPanel = false;

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

  toggleExecutionPanel(): void {
    this.showExecutionPanel = !this.showExecutionPanel;
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
    this.showExecutionPanel = true;
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

  goBack(): void {
    this.router.navigate(['/home/workflows']);
  }
}
