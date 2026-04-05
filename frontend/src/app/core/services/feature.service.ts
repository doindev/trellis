import { Injectable, signal, computed } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';

export interface FeatureFlags {
  langchain4j: boolean;
  swagger: boolean;
  mcpServer: boolean;
  git: boolean;
  frontend: boolean;
}

/**
 * Fetches runtime feature flags from the backend at startup.
 * The backend detects which optional dependencies are on the classpath
 * and reports them here so the UI can hide unavailable features.
 *
 * Loaded via APP_INITIALIZER so flags are resolved BEFORE any component renders.
 * Defaults are false (hidden) — features only show when the backend confirms them.
 */
@Injectable({ providedIn: 'root' })
export class FeatureService {
  private readonly flags = signal<FeatureFlags>({
    langchain4j: false,
    swagger: false,
    mcpServer: false,
    git: false,
    frontend: false
  });

  readonly loaded = signal(false);

  readonly langchain4j = computed(() => this.flags().langchain4j);
  readonly swagger     = computed(() => this.flags().swagger);
  readonly mcpServer   = computed(() => this.flags().mcpServer);
  readonly git         = computed(() => this.flags().git);
  readonly frontend    = computed(() => this.flags().frontend);

  constructor(private api: ApiService) {}

  /** Called by APP_INITIALIZER — blocks app bootstrap until flags are resolved. */
  loadAsync(): Promise<void> {
    console.log('[FeatureService] Loading feature flags...');
    return firstValueFrom(this.api.get<FeatureFlags>('/features'))
      .then(flags => {
        console.log('[FeatureService] Received flags:', JSON.stringify(flags));
        this.flags.set(flags);
        this.loaded.set(true);
        console.log('[FeatureService] Applied — langchain4j=%s, mcpServer=%s, git=%s, swagger=%s',
          this.langchain4j(), this.mcpServer(), this.git(), this.swagger());
      })
      .catch(err => {
        console.error('[FeatureService] Failed to load flags, keeping all disabled:', err);
        this.loaded.set(true);
      });
  }

  /** Legacy sync call — no-op if already loaded by APP_INITIALIZER. */
  load(): void {
    if (this.loaded()) return;
    this.loadAsync();
  }
}
