import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WorkflowNode, NodeTypeDescription, NodeParameter } from '../../../../core/models';
import { StringParamComponent } from './parameter-renderers/string-param.component';
import { NumberParamComponent } from './parameter-renderers/number-param.component';
import { BooleanParamComponent } from './parameter-renderers/boolean-param.component';
import { OptionsParamComponent } from './parameter-renderers/options-param.component';
import { JsonParamComponent } from './parameter-renderers/json-param.component';
import { CollectionParamComponent } from './parameter-renderers/collection-param.component';
import { FixedCollectionParamComponent } from './parameter-renderers/fixed-collection-param.component';

@Component({
  selector: 'app-parameter-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    StringParamComponent,
    NumberParamComponent,
    BooleanParamComponent,
    OptionsParamComponent,
    JsonParamComponent,
    CollectionParamComponent,
    FixedCollectionParamComponent,
  ],
  templateUrl: './parameter-panel.component.html',
  styleUrl: './parameter-panel.component.scss'
})
export class ParameterPanelComponent {
  @Input() node!: WorkflowNode;
  @Input() nodeType?: NodeTypeDescription;
  @Output() parameterChanged = new EventEmitter<Record<string, any>>();
  @Output() close = new EventEmitter<void>();
  @Output() deleteNode = new EventEmitter<void>();

  activeTab: 'parameters' | 'settings' = 'parameters';

  get parameters(): NodeParameter[] {
    return this.nodeType?.parameters || [];
  }

  get visibleParameters(): NodeParameter[] {
    return this.parameters.filter(p => this.isVisible(p));
  }

  isVisible(param: NodeParameter): boolean {
    if (!param.displayOptions) return true;
    const show = param.displayOptions.show;
    if (!show) return true;

    return Object.entries(show).every(([key, values]: [string, any]) => {
      const currentVal = this.node.parameters[key];
      if (Array.isArray(values)) {
        return values.includes(currentVal);
      }
      return currentVal === values;
    });
  }

  onParameterChange(name: string, value: any): void {
    const updated = { ...this.node.parameters, [name]: value };
    this.parameterChanged.emit(updated);
  }

  getParameterValue(name: string, defaultValue: any): any {
    return this.node.parameters[name] ?? defaultValue;
  }
}
