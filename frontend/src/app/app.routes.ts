import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: '/home/workflows', pathMatch: 'full' },
  { path: 'home', redirectTo: '/home/workflows', pathMatch: 'full' },
  {
    path: 'home/chat',
    loadComponent: () =>
      import('./features/chat/chat.component').then(m => m.ChatComponent)
  },
  {
    path: 'home/chat/:view',
    loadComponent: () =>
      import('./features/chat/chat.component').then(m => m.ChatComponent)
  },
  {
    path: 'home/:tab',
    loadComponent: () =>
      import('./features/home/home.component').then(m => m.HomeComponent)
  },
  {
    path: 'workflow/new',
    loadComponent: () =>
      import('./features/workflow-editor/workflow-editor.component').then(m => m.WorkflowEditorComponent)
  },
  {
    path: 'workflow/:id',
    loadComponent: () =>
      import('./features/workflow-editor/workflow-editor.component').then(m => m.WorkflowEditorComponent)
  },
  {
    path: 'agent/new',
    loadComponent: () =>
      import('./features/workflow-editor/workflow-editor.component').then(m => m.WorkflowEditorComponent)
  },
  {
    path: 'agent/:id',
    loadComponent: () =>
      import('./features/workflow-editor/workflow-editor.component').then(m => m.WorkflowEditorComponent)
  },
  { path: 'insights', redirectTo: '/insights/total', pathMatch: 'full' },
  {
    path: 'insights/:metric',
    loadComponent: () =>
      import('./features/insights/insights.component').then(m => m.InsightsComponent)
  },
  { path: 'settings', redirectTo: '/settings/usage', pathMatch: 'full' },
  {
    path: 'settings/:section',
    loadComponent: () =>
      import('./features/settings/settings.component').then(m => m.SettingsComponent)
  },
  { path: '**', redirectTo: '/home/workflows' }
];
