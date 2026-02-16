export interface Credential {
  id?: string;
  name: string;
  type: string;
  data?: Record<string, any>;
  createdAt?: string;
  updatedAt?: string;
}

export interface CredentialSchema {
  type: string;
  displayName: string;
  properties: CredentialProperty[];
}

export interface CredentialProperty {
  name: string;
  displayName: string;
  type: string;
  required: boolean;
  default?: any;
  placeholder?: string;
}
