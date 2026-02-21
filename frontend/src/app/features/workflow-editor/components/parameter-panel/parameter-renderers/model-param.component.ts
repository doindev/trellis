import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { NodeParameter, ModelInfo } from '../../../../../core/models';
import { CredentialService } from '../../../../../core/services';

@Component({
  selector: 'app-model-param',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="param-header">
      <label class="param-label">
        {{ param.displayName }}
        @if (param.required) { <span class="required">*</span> }
      </label>
      @if (isComboboxProvider) {
        <div class="expr-radio-group" role="radiogroup">
          <label class="expr-radio" [class.active]="!customMode" (click)="setDropdownMode()">
            <div class="expr-radio-btn">From list</div>
          </label>
          <label class="expr-radio" [class.active]="customMode" (click)="setCustomMode()">
            <div class="expr-radio-btn">Custom</div>
          </label>
        </div>
      }
    </div>
    @if (param.description) {
      <p class="param-description">{{ param.description }}</p>
    }
    @if (customMode) {
      <input type="text"
             class="form-control param-input"
             [ngModel]="value"
             (ngModelChange)="valueChange.emit($event)"
             placeholder="Enter model ID..."
             [disabled]="readOnly">
    } @else {
      <select class="form-select param-input"
              [ngModel]="value"
              (ngModelChange)="valueChange.emit($event)"
              [disabled]="readOnly || loading">
        @if (loading) {
          <option [ngValue]="value">Loading models...</option>
        } @else {
          <option [ngValue]="null">-- Select model --</option>
          @for (model of displayModels; track model.id) {
            <option [ngValue]="model.id">{{ model.name }}</option>
          }
        }
      </select>
      @if (loadError && !loading) {
        <p class="load-error">Could not load models from API. Showing defaults.</p>
      }
    }
  `,
  styles: [`
    .param-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
    .param-label { font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); margin: 0; }
    .required { color: var(--trellis-error-color); }
    .param-description { font-size: 0.6875rem; color: hsl(0,0%,58%); margin-bottom: 6px; }
    .param-input {
      background: hsl(0,0%,9%);
      border: 1px solid hsl(0,0%,24%);
      color: hsl(0,0%,96%);
      font-size: 0.8125rem;
      border-radius: 6px;
    }
    .param-input:focus { background: hsl(0,0%,9%); border-color: hsl(247,49%,53%); box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15); color: hsl(0,0%,96%); }
    .param-input option { background: hsl(0,0%,13%); color: hsl(0,0%,96%); }
    .load-error { font-size: 0.6875rem; color: hsl(30,80%,55%); margin-top: 4px; }
    .expr-radio-group {
      display: inline-flex;
      border-radius: 6px;
      overflow: hidden;
      border: 1px solid hsl(0,0%,24%);
      flex-shrink: 0;
    }
    .expr-radio { cursor: pointer; margin: 0; }
    .expr-radio-btn {
      padding: 2px 8px;
      font-size: 0.6875rem;
      font-weight: 500;
      color: hsl(0,0%,58%);
      background: hsl(0,0%,13%);
      transition: all 0.15s;
      user-select: none;
      white-space: nowrap;
    }
    .expr-radio.active .expr-radio-btn {
      background: hsl(0,0%,20%);
      color: hsl(0,0%,96%);
    }
    .expr-radio:hover .expr-radio-btn { color: hsl(0,0%,80%); }
  `]
})
export class ModelParamComponent implements OnChanges {
  @Input() param!: NodeParameter;
  @Input() value: any;
  @Input() readOnly = false;
  @Input() credentialId: string | null = null;
  @Input() credentialType = '';
  @Input() modelType = 'chat';
  @Output() valueChange = new EventEmitter<any>();

  loading = false;
  loadError = false;
  customMode = false;
  dynamicModels: ModelInfo[] = [];
  private loadSub?: Subscription;

  constructor(private credentialService: CredentialService) {}

  get isComboboxProvider(): boolean {
    return this.credentialType === 'anthropicApi' || this.credentialType === 'ollamaApi';
  }

  get displayModels(): { id: string; name: string }[] {
    if (this.dynamicModels.length > 0) return this.dynamicModels;
    return (this.param.options || []).map(o => ({ id: o.value, name: o.name }));
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['credentialId']) {
      if (this.credentialId) {
        this.loadModels();
      } else {
        this.dynamicModels = [];
        this.loadError = false;
      }
    }
  }

  private loadModels(): void {
    if (!this.credentialId) return;
    this.loadSub?.unsubscribe();
    this.loading = true;
    this.loadError = false;
    this.loadSub = this.credentialService.listModels(this.credentialId, this.modelType).subscribe({
      next: (models) => {
        this.dynamicModels = models;
        this.loading = false;
      },
      error: () => {
        this.dynamicModels = [];
        this.loadError = true;
        this.loading = false;
      }
    });
  }

  setDropdownMode(): void {
    this.customMode = false;
  }

  setCustomMode(): void {
    this.customMode = true;
  }
}
