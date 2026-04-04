import { Injectable, signal, computed } from '@angular/core';
import { ApiService } from './api.service';

export interface FeatureFlags {
  langchain4j: boolean;
  swagger: boolean;
  mcpServer: boolean;
  frontend: boolean;
}

/**
 * Fetches runtime feature flags from the backend at startup.
 * The backend detects which optional dependencies are on the classpath
 * and reports them here so the UI can hide unavailable features.
 */
@Injectable({ providedIn: 'root' })
export class FeatureService {
  private readonly flags = signal<FeatureFlags>({
    langchain4j: true,
    swagger: true,
    mcpServer: true,
    frontend: true
  });

  readonly loaded = signal(false);

  readonly langchain4j = computed(() => this.flags().langchain4j);
  readonly swagger     = computed(() => this.flags().swagger);
  readonly mcpServer   = computed(() => this.flags().mcpServer);
  readonly frontend    = computed(() => this.flags().frontend);

  constructor(private api: ApiService) {}

  load(): void {
    if (this.loaded()) return;
    this.api.get<FeatureFlags>('/features').subscribe({
      next: (flags) => {
        this.flags.set(flags);
        this.loaded.set(true);
      },
      error: () => {
        // If the endpoint is unreachable, default to all enabled
        this.loaded.set(true);
      }
    });
  }
}
