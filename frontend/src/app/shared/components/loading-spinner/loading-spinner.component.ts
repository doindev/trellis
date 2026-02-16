import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-loading-spinner',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="spinner-container" [class.overlay]="overlay">
      <div class="spinner-border" [class]="'text-' + color" [style.width.rem]="size" [style.height.rem]="size" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
      @if (message) {
        <p class="spinner-message">{{ message }}</p>
      }
    </div>
  `,
  styles: [`
    .spinner-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 2rem;
    }
    .spinner-container.overlay {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: hsla(0, 0%, 9%, 0.8);
      z-index: 100;
    }
    .spinner-message {
      margin-top: 1rem;
      color: hsl(0, 0%, 68%);
      font-size: 0.875rem;
    }
  `]
})
export class LoadingSpinnerComponent {
  @Input() size = 2;
  @Input() color = 'primary';
  @Input() message = '';
  @Input() overlay = false;
}
