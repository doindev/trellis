import { Component, Input, Output, EventEmitter, ElementRef, HostListener } from '@angular/core';
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
  @Output() share = new EventEmitter<Workflow>();
  @Output() duplicate = new EventEmitter<Workflow>();
  @Output() move = new EventEmitter<Workflow>();
  @Output() archive = new EventEmitter<Workflow>();
  @Output() enableMcp = new EventEmitter<Workflow>();
  showActions = false;
  dropdownStyle: Record<string, string> = {};
  private actionsCloseTimer: ReturnType<typeof setTimeout> | null = null;
  private scrollListener: (() => void) | null = null;

  constructor(private elRef: ElementRef) {}

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
    if (this.showActions) {
      this.closeDropdown();
      return;
    }
    const btn = event.currentTarget as HTMLElement;
    const rect = btn.getBoundingClientRect();
    const dropdownHeight = 260; // approximate max height of dropdown
    const spaceBelow = window.innerHeight - rect.bottom;
    const openUp = spaceBelow < dropdownHeight && rect.top > dropdownHeight;

    this.dropdownStyle = {
      position: 'fixed',
      right: (window.innerWidth - rect.right) + 'px',
      ...(openUp
        ? { bottom: (window.innerHeight - rect.top + 4) + 'px' }
        : { top: (rect.bottom + 4) + 'px' }),
      'z-index': '9999'
    };
    this.showActions = true;
    this.addScrollListener();
  }

  scheduleActionsClose(): void {
    this.cancelActionsClose();
    this.actionsCloseTimer = setTimeout(() => {
      this.closeDropdown();
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
    this.closeDropdown();
    switch (action) {
      case 'open': this.open.emit(this.workflow); break;
      case 'share': this.share.emit(this.workflow); break;
      case 'duplicate': this.duplicate.emit(this.workflow); break;
      case 'move': this.move.emit(this.workflow); break;
      case 'archive': this.archive.emit(this.workflow); break;
      case 'enableMcp': this.enableMcp.emit(this.workflow); break;
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    if (this.showActions && !this.elRef.nativeElement.contains(event.target)) {
      this.closeDropdown();
    }
  }

  private closeDropdown(): void {
    this.showActions = false;
    this.removeScrollListener();
  }

  private addScrollListener(): void {
    this.removeScrollListener();
    const handler = () => this.closeDropdown();
    // Listen on capture phase to catch scroll on any ancestor
    document.addEventListener('scroll', handler, true);
    this.scrollListener = () => document.removeEventListener('scroll', handler, true);
  }

  private removeScrollListener(): void {
    if (this.scrollListener) {
      this.scrollListener();
      this.scrollListener = null;
    }
  }
}
