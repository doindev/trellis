import { Component, EventEmitter, HostListener, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-agent-control-overlay',
    imports: [CommonModule],
    template: `
    @if (active) {
      <div class="aco-bar">
        <div class="aco-indicator"></div>
        <span class="aco-text">AI Agent is controlling this page</span>
        <button class="aco-stop" (click)="onStop()">Stop</button>
        <span class="aco-hint">ESC</span>
      </div>
    }
  `,
    styles: [`
    .aco-bar {
      position: fixed;
      top: 0;
      left: 50%;
      transform: translateX(-50%);
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 6px 16px;
      background: #1e293b;
      color: #f1f5f9;
      border-radius: 0 0 10px 10px;
      font-size: 13px;
      z-index: 9999;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
      user-select: none;
    }
    .aco-indicator {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #22c55e;
      animation: aco-pulse 2s ease-in-out infinite;
    }
    @keyframes aco-pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.4; }
    }
    .aco-text {
      font-weight: 500;
    }
    .aco-stop {
      padding: 3px 12px;
      border-radius: 6px;
      border: 1px solid rgba(255, 255, 255, 0.2);
      background: rgba(255, 255, 255, 0.1);
      color: #f1f5f9;
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
      transition: background-color 0.15s;
    }
    .aco-stop:hover {
      background: rgba(239, 68, 68, 0.7);
      border-color: rgba(239, 68, 68, 0.5);
    }
    .aco-hint {
      font-size: 11px;
      color: #94a3b8;
      border: 1px solid #475569;
      border-radius: 4px;
      padding: 1px 5px;
    }
  `]
})
export class AgentControlOverlayComponent {
  @Input() active = false;
  @Output() stop = new EventEmitter<void>();

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.active) {
      this.onStop();
    }
  }

  onStop(): void {
    this.stop.emit();
  }
}
