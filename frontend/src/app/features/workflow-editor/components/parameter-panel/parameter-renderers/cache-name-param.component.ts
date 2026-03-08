import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CacheService, CacheDefinition } from '../../../../../core/services/cache.service';
import { NodeParameter } from '../../../../../core/models';

@Component({
    selector: 'app-cache-name-param',
    imports: [CommonModule, FormsModule],
    template: `
    <div class="param-group">
      <div class="param-header">
        <label class="param-label">{{ param.displayName }}</label>
      </div>
      @if (cacheSource === 'select') {
        <select class="form-select param-input"
                [ngModel]="value"
                (ngModelChange)="valueChange.emit($event)"
                [disabled]="readOnly">
          <option [ngValue]="null">-- Select cache --</option>
          @for (cache of caches; track cache.id) {
            <option [ngValue]="cache.name">{{ cache.name }}</option>
          }
        </select>
      } @else {
        <input type="text" class="form-control param-input"
               [ngModel]="value"
               (ngModelChange)="valueChange.emit($event)"
               [disabled]="readOnly"
               placeholder="Enter cache name" />
        @if (conflictWarning) {
          <div class="conflict-warning">{{ conflictWarning }}</div>
        }
      }
    </div>
  `,
    styles: [`
    .param-group { margin-bottom: 12px; }
    .param-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
    .param-label { font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); margin: 0; }
    .param-input {
      background: hsl(0,0%,9%);
      border: 1px solid hsl(0,0%,24%);
      color: hsl(0,0%,96%);
      font-size: 0.8125rem;
      border-radius: 6px;
    }
    .param-input:focus { background: hsl(0,0%,9%); border-color: hsl(247,49%,53%); box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15); color: hsl(0,0%,96%); }
    .param-input option { background: hsl(0,0%,13%); color: hsl(0,0%,96%); }
    .conflict-warning {
      margin-top: 4px;
      font-size: 0.75rem;
      color: hsl(40, 95%, 60%);
    }
  `]
})
export class CacheNameParamComponent implements OnInit {
  @Input() param!: NodeParameter;
  @Input() value: any;
  @Input() readOnly = false;
  @Input() projectId = '';
  @Input() cacheSource: string = 'select';
  @Output() valueChange = new EventEmitter<any>();

  caches: CacheDefinition[] = [];

  constructor(private cacheService: CacheService) {}

  ngOnInit(): void {
    const obs = this.projectId
      ? this.cacheService.listByProject(this.projectId)
      : this.cacheService.list();
    obs.subscribe(caches => this.caches = caches);
  }

  get conflictWarning(): string | null {
    if (this.cacheSource !== 'inline' || !this.value) return null;
    const match = this.caches.find(c => c.name === this.value);
    return match ? `"${this.value}" matches a defined cache. Use "Select Defined Cache" to use it.` : null;
  }
}
