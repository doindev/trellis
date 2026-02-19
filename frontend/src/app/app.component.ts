import { Component, OnInit, OnDestroy, HostListener, ElementRef } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription, filter } from 'rxjs';
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
export class AppComponent implements OnInit, OnDestroy {
  title = 'Trellis';
  sidebarCollapsed = false;
  showAddDropdown = false;
  showCreateMenu = false;
  settingsMode = false;
  showSettingsPopover = false;
  projects: Project[] = [];
  showCreateProjectModal = false;
  newProjectName = '';
  newProjectDescription = '';
  newProjectIcon = '';
  creatingProject = false;
  private addDropdownTimer: ReturnType<typeof setTimeout> | null = null;
  private createMenuTimer: ReturnType<typeof setTimeout> | null = null;
  private recentlyFocused = false;
  private routerSub?: Subscription;

  @HostListener('window:focus')
  onWindowFocus(): void {
    this.recentlyFocused = true;
    setTimeout(() => this.recentlyFocused = false, 300);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.showSettingsPopover) {
      const target = event.target as HTMLElement;
      if (!target.closest('.settings-popover-wrapper')) {
        this.showSettingsPopover = false;
      }
    }
  }

  constructor(private router: Router, private projectService: ProjectService) {}

  ngOnInit(): void {
    this.loadProjects();
    this.settingsMode = this.router.url.startsWith('/settings');
    this.routerSub = this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd)
    ).subscribe(e => {
      this.settingsMode = e.urlAfterRedirects.startsWith('/settings');
    });
  }

  ngOnDestroy(): void {
    this.routerSub?.unsubscribe();
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
    if (this.recentlyFocused) return;
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
    this.router.navigate(['/home/credentials'], { queryParams: { action: 'create-credential' } });
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

  toggleSettingsPopover(): void {
    this.showSettingsPopover = !this.showSettingsPopover;
  }

  onSettingsNav(section: string): void {
    this.showSettingsPopover = false;
    this.router.navigate(['/settings', section]);
  }

  enterSettings(): void {
    this.router.navigate(['/settings/usage']);
  }

  exitSettings(): void {
    this.router.navigate(['/home/workflows']);
  }
}
