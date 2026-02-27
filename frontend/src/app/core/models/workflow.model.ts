export interface Tag {
  id: string;
  name: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Workflow {
  id?: string;
  projectId?: string;
  name: string;
  description?: string;
  published: boolean;
  archived?: boolean;
  currentVersion: number;
  versionIsDirty?: boolean;
  nodes: WorkflowNode[];
  connections: Record<string, any>;
  settings?: Record<string, any>;
  staticData?: Record<string, any>;
  pinData?: Record<string, any>;
  mcpEnabled?: boolean;
  mcpDescription?: string;
  mcpInputSchema?: any[];
  mcpOutputSchema?: any;
  swaggerEnabled?: boolean;
  tags?: Tag[];
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
  alwaysOutputData?: boolean;
  executeOnce?: boolean;
  retryOnFail?: boolean;
  maxTries?: number;
  waitBetweenTries?: number;
  onError?: string;
  notes?: string;
  notesInFlow?: boolean;
}

export interface WorkflowVersion {
  id: string;
  workflowId: string;
  versionNumber: number;
  versionName: string;
  description?: string;
  publishedAt: string;
}
