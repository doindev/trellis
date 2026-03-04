export interface NodeTypeDescription {
  type: string;
  displayName: string;
  description: string;
  category: string;
  version: number;
  icon: string;
  isTrigger: boolean;
  isPolling: boolean;
  credentials: string[];
  group: string;
  subtitle: string;
  documentationUrl: string;
  searchOnly: boolean;
  other: boolean;
  parameters: NodeParameter[];
  inputs: NodeIO[];
  outputs: NodeIO[];
}

export interface NodeParameter {
  name: string;
  displayName: string;
  description: string;
  type: string;
  defaultValue: any;
  required: boolean;
  placeHolder: string;
  options: ParameterOption[];
  displayOptions: any;
  typeOptions: any;
  nestedParameters: NodeParameter[];
  noDataExpression: boolean;
  isNodeSetting: boolean;
}

export interface ParameterOption {
  name: string;
  value: any;
  description: string;
  action?: string;
}

export interface NodeIO {
  name: string;
  displayName: string;
  type: string;
  required: boolean;
  maxConnections?: number;
}
