import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { SettingsService, UsageStats, UserInfo, ApiKeyInfo } from '../../core/services/settings.service';
import { NodeTypeService } from '../../core/services/node-type.service';
import { NodeTypeDescription } from '../../core/models';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent implements OnInit, OnDestroy {
  activeSection = 'usage';
  private routeSub?: Subscription;

  // Usage
  usage: UsageStats | null = null;

  // Personal
  personalForm = { firstName: '', lastName: '', email: '', currentPassword: '', newPassword: '' };
  personalSaving = false;

  // Users
  users: UserInfo[] = [];
  showAddUser = false;
  newUser = { email: '', firstName: '', lastName: '', password: '', role: 'editor' };
  addingUser = false;

  // API Keys
  apiKeys: ApiKeyInfo[] = [];
  showCreateKey = false;
  newKeyLabel = '';
  creatingKey = false;
  createdKeyValue: string | null = null;
  copiedKey = false;

  // Community Nodes
  nodeTypes: NodeTypeDescription[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private settingsService: SettingsService,
    private nodeTypeService: NodeTypeService
  ) {}

  ngOnInit(): void {
    this.routeSub = this.route.paramMap.subscribe(params => {
      this.activeSection = params.get('section') || 'usage';
      this.loadSectionData();
    });
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
  }

  private loadSectionData(): void {
    switch (this.activeSection) {
      case 'usage':
        this.settingsService.getUsage().subscribe({
          next: data => this.usage = data,
          error: () => this.usage = null
        });
        break;
      case 'personal':
        this.settingsService.listUsers().subscribe({
          next: users => {
            const owner = users.find(u => u.role === 'owner') || users[0];
            if (owner) {
              this.personalForm.firstName = owner.firstName || '';
              this.personalForm.lastName = owner.lastName || '';
              this.personalForm.email = owner.email || '';
            }
          }
        });
        break;
      case 'users':
        this.loadUsers();
        break;
      case 'api':
        this.loadApiKeys();
        break;
      case 'community-nodes':
        this.nodeTypeService.getAll().subscribe({
          next: types => this.nodeTypes = types
        });
        break;
    }
  }

  private loadUsers(): void {
    this.settingsService.listUsers().subscribe({
      next: users => this.users = users,
      error: () => this.users = []
    });
  }

  private loadApiKeys(): void {
    this.settingsService.listApiKeys().subscribe({
      next: keys => this.apiKeys = keys,
      error: () => this.apiKeys = []
    });
  }

  // Personal actions
  savePersonal(): void {
    this.personalSaving = true;
    this.settingsService.listUsers().subscribe({
      next: users => {
        const owner = users.find(u => u.role === 'owner') || users[0];
        if (!owner) { this.personalSaving = false; return; }
        const data: any = {
          firstName: this.personalForm.firstName,
          lastName: this.personalForm.lastName,
          email: this.personalForm.email
        };
        if (this.personalForm.newPassword) {
          data.password = this.personalForm.newPassword;
        }
        this.settingsService.updateUser(owner.id, data).subscribe({
          next: () => {
            this.personalSaving = false;
            this.personalForm.currentPassword = '';
            this.personalForm.newPassword = '';
          },
          error: () => this.personalSaving = false
        });
      }
    });
  }

  // User actions
  addUser(): void {
    if (!this.newUser.email || !this.newUser.password) return;
    this.addingUser = true;
    this.settingsService.createUser(this.newUser).subscribe({
      next: () => {
        this.addingUser = false;
        this.showAddUser = false;
        this.newUser = { email: '', firstName: '', lastName: '', password: '', role: 'editor' };
        this.loadUsers();
      },
      error: () => this.addingUser = false
    });
  }

  deleteUser(id: string): void {
    this.settingsService.deleteUser(id).subscribe({
      next: () => this.loadUsers()
    });
  }

  // API Key actions
  createApiKey(): void {
    if (!this.newKeyLabel) return;
    this.creatingKey = true;
    this.settingsService.createApiKey(this.newKeyLabel).subscribe({
      next: key => {
        this.creatingKey = false;
        this.createdKeyValue = key.apiKey || null;
        this.newKeyLabel = '';
        this.showCreateKey = false;
        this.loadApiKeys();
      },
      error: () => this.creatingKey = false
    });
  }

  copyApiKey(): void {
    if (this.createdKeyValue) {
      navigator.clipboard.writeText(this.createdKeyValue);
      this.copiedKey = true;
      setTimeout(() => this.copiedKey = false, 2000);
    }
  }

  dismissCreatedKey(): void {
    this.createdKeyValue = null;
    this.copiedKey = false;
  }

  deleteApiKey(id: string): void {
    this.settingsService.deleteApiKey(id).subscribe({
      next: () => this.loadApiKeys()
    });
  }

  getRoleLabel(role: string): string {
    switch (role) {
      case 'owner': return 'Owner';
      case 'admin': return 'Admin';
      case 'editor': return 'Editor';
      case 'viewer': return 'Viewer';
      default: return role;
    }
  }
}
