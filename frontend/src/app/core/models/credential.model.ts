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
  description?: string;
  category?: string;
  icon?: string;
  documentationUrl?: string;
  extendsType?: string;
  properties: CredentialProperty[];
}

export interface CredentialProperty {
  name: string;
  displayName: string;
  description?: string;
  type: string;
  required: boolean;
  defaultValue?: any;
  placeHolder?: string;
  options?: { name: string; value: any }[];
  typeOptions?: Record<string, any>;
  displayOptions?: { show?: Record<string, any[]> };
}
