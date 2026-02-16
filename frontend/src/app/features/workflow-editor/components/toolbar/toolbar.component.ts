import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-toolbar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './toolbar.component.html',
  styleUrl: './toolbar.component.scss'
})
export class ToolbarComponent {
  @Input() workflowName = '';
  @Input() isActive = false;
  @Input() isDirty = false;
  @Input() isExecuting = false;
  @Output() nameChanged = new EventEmitter<string>();
  @Output() save = new EventEmitter<void>();
  @Output() execute = new EventEmitter<void>();
  @Output() toggleActive = new EventEmitter<void>();
  @Output() back = new EventEmitter<void>();
  @Output() togglePalette = new EventEmitter<void>();
  @Output() showExecutions = new EventEmitter<void>();

  editingName = false;
  nameInput = '';

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
}
