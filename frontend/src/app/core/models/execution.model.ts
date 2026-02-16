export interface Execution {
  id: string;
  workflowId: string;
  workflowData?: any;
  status: string;
  mode: string;
  resultData?: any;
  startedAt?: string;
  finishedAt?: string;
  errorMessage?: string;
}
