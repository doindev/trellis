import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'Trellis';
  sidebarCollapsed = false;
  showAddDropdown = false;

  constructor(private router: Router) {}

  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  onAddWorkflow(): void {
    this.showAddDropdown = false;
    this.router.navigate(['/workflow/new']);
  }

  onAddCredential(): void {
    this.showAddDropdown = false;
    this.router.navigate(['/home/credentials']);
  }
}
