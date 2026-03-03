export interface Project {
  id?: string;
  name: string;
  type: 'PERSONAL' | 'TEAM';
  icon?: ProjectIcon;
  description?: string;
  contextPath?: string;
  members?: ProjectMember[];
  workflowCount?: number;
  credentialCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface ProjectIcon {
  type: 'emoji' | 'icon';
  value: string;
}

export interface ProjectMember {
  userId: string;
  email: string;
  firstName?: string;
  lastName?: string;
  role: 'PROJECT_PERSONAL_OWNER' | 'PROJECT_ADMIN' | 'PROJECT_EDITOR' | 'PROJECT_VIEWER';
  createdAt?: string;
}
