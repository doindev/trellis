import { Component, OnInit } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, LucideIconProvider, LUCIDE_ICONS, PanelLeftClose, PanelLeftOpen, KeyRound, Folder, Table, Plus, Variable, Workflow } from 'lucide-angular';
import { ProjectService } from './core/services/project.service';
import { Project } from './core/models/project.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterOutlet, RouterLink, RouterLinkActive, LucideAngularModule],
  providers: [{ provide: LUCIDE_ICONS, multi: true, useValue: new LucideIconProvider({ PanelLeftClose, PanelLeftOpen, KeyRound, Folder, Table, Plus, Variable, Workflow }) }],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {
  title = 'Trellis';
  sidebarCollapsed = false;
  showAddDropdown = false;
  showCreateMenu = false;
  projects: Project[] = [];
  showCreateProjectModal = false;
  newProjectName = '';
  newProjectDescription = '';
  newProjectIcon = '';
  creatingProject = false;
  private addDropdownTimer: ReturnType<typeof setTimeout> | null = null;
  private createMenuTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private router: Router, private projectService: ProjectService) {}

  ngOnInit(): void {
    this.loadProjects();
  }

  loadProjects(): void {
    this.projectService.list().subscribe({
      next: (projects) => this.projects = projects.filter(p => p.type === 'TEAM'),
      error: () => this.projects = []
    });
  }

  getProjectIcon(project: Project): string {
    if (project.icon?.type === 'emoji' && project.icon.value) {
      return project.icon.value;
    }
    return '';
  }

  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  scheduleAddDropdownClose(): void {
    this.cancelAddDropdownClose();
    this.addDropdownTimer = setTimeout(() => {
      this.showAddDropdown = false;
    }, 300);
  }

  cancelAddDropdownClose(): void {
    if (this.addDropdownTimer) {
      clearTimeout(this.addDropdownTimer);
      this.addDropdownTimer = null;
    }
  }

  scheduleCreateClose(): void {
    this.cancelCreateClose();
    this.createMenuTimer = setTimeout(() => {
      this.showCreateMenu = false;
    }, 300);
  }

  cancelCreateClose(): void {
    if (this.createMenuTimer) {
      clearTimeout(this.createMenuTimer);
      this.createMenuTimer = null;
    }
  }

  onAddWorkflow(): void {
    this.showAddDropdown = false;
    this.showCreateMenu = false;
    this.router.navigate(['/workflow/new']);
  }

  onAddCredential(): void {
    this.showAddDropdown = false;
    this.showCreateMenu = false;
    this.router.navigate(['/home/credentials']);
  }

  onAddProject(): void {
    this.showAddDropdown = false;
    this.showCreateMenu = false;
    this.newProjectName = '';
    this.newProjectDescription = '';
    this.newProjectIcon = '';
    this.showCreateProjectModal = true;
  }

  onCreateProjectConfirm(): void {
    if (!this.newProjectName.trim() || this.creatingProject) return;
    this.creatingProject = true;
    const icon = this.newProjectIcon.trim()
      ? { type: 'emoji', value: this.newProjectIcon.trim() } as any
      : undefined;
    this.projectService.create({
      name: this.newProjectName.trim(),
      description: this.newProjectDescription.trim() || undefined,
      icon
    }).subscribe({
      next: (project) => {
        this.creatingProject = false;
        this.showCreateProjectModal = false;
        this.loadProjects();
        this.router.navigate(['/projects', project.id, 'workflows']);
      },
      error: () => this.creatingProject = false
    });
  }

  onCreateProjectCancel(): void {
    this.showCreateProjectModal = false;
  }

  onAddVariable(): void {
    this.showCreateMenu = false;
    this.router.navigate(['/home/variables']);
  }

  onSearch(): void {
    this.router.navigate(['/home/search']);
  }

  onAddFolder(): void {
    this.showCreateMenu = false;
    this.router.navigate(['/home/folders']);
  }

  onAddDataTable(): void {
    this.showCreateMenu = false;
    this.router.navigate(['/home/data-tables']);
  }
}
