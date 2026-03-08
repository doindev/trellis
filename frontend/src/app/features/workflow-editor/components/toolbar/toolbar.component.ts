import { Component, Input, Output, EventEmitter, HostListener, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, LucideIconProvider, LUCIDE_ICONS, ClockCheck, ClockPlus, ClockFading, Aperture } from 'lucide-angular';
import { Tag } from '../../../../core/models';

export type ToolbarAction =
  | 'editDescription'
  | 'editMcpParams'
  | 'duplicate'
  | 'download'
  | 'rename'
  | 'importFromUri'
  | 'importFromFile'
  | 'pushToGit'
  | 'settings'
  | 'unpublish'
  | 'archive';

@Component({
    selector: 'app-toolbar',
    imports: [CommonModule, FormsModule, LucideAngularModule],
    providers: [{
            provide: LUCIDE_ICONS,
            multi: true,
            useValue: new LucideIconProvider({ ClockCheck, ClockPlus, ClockFading, Aperture })
        }],
    templateUrl: './toolbar.component.html',
    styleUrl: './toolbar.component.scss'
})
export class ToolbarComponent {
  @Input() workflowName = '';
  @Input() published = false;
  @Input() currentVersion = 0;
  @Input() versionIsDirty = false;
  @Input() tags: Tag[] = [];

  get publishStatus(): 'current' | 'stale' | 'unpublished' {
    if (!this.published) return 'unpublished';
    return this.versionIsDirty ? 'stale' : 'current';
  }
  @Input() isPersonalProject = false;
  @Input() projectName = 'Personal';
  @Input() isDirty = false;
  @Input() isExecuting = false;
  @Input() isSaving = false;
  @Output() nameChanged = new EventEmitter<string>();
  @Output() save = new EventEmitter<void>();
  @Output() toggleVersions = new EventEmitter<void>();
  @Output() execute = new EventEmitter<void>();
  @Output() publish = new EventEmitter<void>();
  @Output() back = new EventEmitter<void>();
  @Output() openTagSelector = new EventEmitter<void>();
  @Input() activeTab: 'editor' | 'executions' = 'editor';
  @Output() showExecutions = new EventEmitter<void>();
  @Output() tabChanged = new EventEmitter<'editor' | 'executions'>();
  @Output() menuAction = new EventEmitter<ToolbarAction>();

  editingName = false;
  nameInput = '';
  showMenu = false;

  constructor(private elRef: ElementRef) {}

  startEditName(): void {
    this.editingName = true;
    this.nameInput = this.workflowName;
  }

  saveName(): void {
    this.editingName = false;
    if (this.nameInput.trim() && this.nameInput !== this.workflowName) {
      this.nameChanged.emit(this.nameInput.trim());
    }
  }

  cancelEditName(): void {
    this.editingName = false;
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      this.saveName();
    } else if (event.key === 'Escape') {
      this.cancelEditName();
    }
  }

  toggleExecutionPanel(): void {
    this.showExecutions.emit();
  }

  toggleMenu(): void {
    this.showMenu = !this.showMenu;
  }

  onMenuAction(action: ToolbarAction): void {
    this.showMenu = false;
    if (action === 'rename') {
      this.startEditName();
    } else {
      this.menuAction.emit(action);
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    if (this.showMenu && !this.elRef.nativeElement.querySelector('.more-menu-wrapper')?.contains(event.target as Node)) {
      this.showMenu = false;
    }
  }
}
