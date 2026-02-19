import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Workflow } from '../../../core/models';

@Component({
  selector: 'app-workflow-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './workflow-card.component.html',
  styleUrl: './workflow-card.component.scss'
})
export class WorkflowCardComponent {
  @Input({ required: true }) workflow!: Workflow;
  @Input() projectName?: string;
  @Output() open = new EventEmitter<Workflow>();
  @Output() duplicate = new EventEmitter<Workflow>();
  @Output() delete = new EventEmitter<Workflow>();
  showActions = false;
  private actionsCloseTimer: ReturnType<typeof setTimeout> | null = null;

  get nodeCount(): number {
    return this.workflow.nodes?.length || 0;
  }

  get timeAgo(): string {
    const date = this.workflow.updatedAt || this.workflow.createdAt;
    if (!date) return '';
    const diff = Date.now() - new Date(date).getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 1) return 'just now';
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    if (days < 30) return `${days}d ago`;
    return new Date(date).toLocaleDateString();
  }

  get createdDate(): string {
    if (!this.workflow.createdAt) return '';
    return new Date(this.workflow.createdAt).toLocaleDateString();
  }

  onCardClick(): void {
    this.open.emit(this.workflow);
  }

  onToggleActions(event: Event): void {
    event.stopPropagation();
    this.cancelActionsClose();
    this.showActions = !this.showActions;
  }

  scheduleActionsClose(): void {
    this.cancelActionsClose();
    this.actionsCloseTimer = setTimeout(() => {
      this.showActions = false;
    }, 400);
  }

  cancelActionsClose(): void {
    if (this.actionsCloseTimer) {
      clearTimeout(this.actionsCloseTimer);
      this.actionsCloseTimer = null;
    }
  }

  onAction(action: string, event: Event): void {
    event.stopPropagation();
    this.showActions = false;
    switch (action) {
      case 'open': this.open.emit(this.workflow); break;
      case 'duplicate': this.duplicate.emit(this.workflow); break;
      case 'delete': this.delete.emit(this.workflow); break;
    }
  }

}
