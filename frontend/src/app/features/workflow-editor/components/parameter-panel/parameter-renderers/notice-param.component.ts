import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NodeParameter } from '../../../../../core/models';

@Component({
  selector: 'app-notice-param',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="notice-block">
      <span class="notice-text" [innerHTML]="cleanDescription"></span>
    </div>
  `,
  styles: [`
    .notice-block {
      padding: 10px 14px;
      background: hsla(36, 60%, 50%, 0.08);
      border: 1px solid hsla(36, 60%, 50%, 0.2);
      border-radius: 6px;
      color: hsl(36, 50%, 70%);
    }
    .notice-text {
      font-size: 0.8125rem;
      line-height: 1.5;
    }
    :host ::ng-deep .notice-text a {
      color: hsl(36, 80%, 70%);
      text-decoration: underline;
    }
    :host ::ng-deep .notice-text a:hover {
      color: hsl(36, 80%, 80%);
    }
  `]
})
export class NoticeParamComponent {
  @Input() param!: NodeParameter;

  get cleanDescription(): string {
    return (this.param.description || '').replace(/<a\b[^>]*>.*?<\/a>/gi, '').trim();
  }
}
