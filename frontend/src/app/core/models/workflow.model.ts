export interface Workflow {
  id?: string;
  name: string;
  active: boolean;
  nodes: WorkflowNode[];
  connections: Record<string, any>;
  settings?: Record<string, any>;
  staticData?: Record<string, any>;
  pinData?: Record<string, any>;
  createdAt?: string;
  updatedAt?: string;
}

export interface WorkflowNode {
  id: string;
  name: string;
  type: string;
  typeVersion: number;
  parameters: Record<string, any>;
  position: [number, number];
  disabled?: boolean;
  credentials?: Record<string, any>;
  continueOnFail?: boolean;
}
