import { Component, Input, Output, EventEmitter, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-publish-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './publish-modal.component.html',
  styleUrl: './publish-modal.component.scss'
})
export class PublishModalComponent implements OnInit {
  @Input() currentVersion = 0;
  @Input() pinnedNodeNames: string[] = [];
  @Output() confirmed = new EventEmitter<{ versionName: string; description: string; includePinData: boolean }>();
  @Output() cancelled = new EventEmitter<void>();

  versionName = '';
  description = '';
  clearPinnedData = true;

  get hasPinnedNodes(): boolean {
    return this.pinnedNodeNames.length > 0;
  }

  ngOnInit(): void {
    this.versionName = `Version ${this.currentVersion + 1}`;
  }

  onConfirm(): void {
    this.confirmed.emit({
      versionName: this.versionName.trim() || `Version ${this.currentVersion + 1}`,
      description: this.description.trim(),
      includePinData: this.hasPinnedNodes && !this.clearPinnedData
    });
  }

  onCancel(): void {
    this.cancelled.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.onCancel();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.onCancel();
  }
}
