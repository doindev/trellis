import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-execution-panel',
    imports: [CommonModule],
    templateUrl: './execution-panel.component.html',
    styleUrl: './execution-panel.component.scss'
})
export class ExecutionPanelComponent {
  @Input() executionData: Record<string, any> | null = null;
  @Input() selectedNodeId: string | null = null;
  @Input() isExecuting = false;
  @Output() close = new EventEmitter<void>();

  get nodeExecutionData(): any {
    if (!this.executionData || !this.selectedNodeId) return null;
    return this.executionData[this.selectedNodeId];
  }

  get outputJson(): string {
    const data = this.nodeExecutionData;
    if (!data) return '';
    return JSON.stringify(data.data || data, null, 2);
  }

  get executionStatus(): string {
    return this.nodeExecutionData?.status || (this.isExecuting ? 'running' : 'idle');
  }
}
