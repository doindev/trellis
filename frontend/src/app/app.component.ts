import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, LucideIconProvider, LUCIDE_ICONS, PanelLeftClose, PanelLeftOpen, KeyRound, Folder, Table, Plus, Variable, Workflow } from 'lucide-angular';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, LucideAngularModule],
  providers: [{ provide: LUCIDE_ICONS, multi: true, useValue: new LucideIconProvider({ PanelLeftClose, PanelLeftOpen, KeyRound, Folder, Table, Plus, Variable, Workflow }) }],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'Trellis';
  sidebarCollapsed = false;
  showAddDropdown = false;
  showCreateMenu = false;
  private addDropdownTimer: ReturnType<typeof setTimeout> | null = null;
  private createMenuTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private router: Router) {}

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
    this.router.navigate(['/home/projects']);
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
