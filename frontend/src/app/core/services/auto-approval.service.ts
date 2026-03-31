import { Injectable } from '@angular/core';

export type ApprovalScope =
  | { type: 'tool'; toolName: string }
  | { type: 'read' }
  | { type: 'write' }
  | { type: 'all' };

/**
 * Manages session-scoped auto-approval rules for MCP tool consent requests.
 * Rules are cleared on page refresh or when the user revokes control.
 */
@Injectable({ providedIn: 'root' })
export class AutoApprovalService {
  private rules: ApprovalScope[] = [];

  addRule(rule: ApprovalScope): void {
    // Avoid duplicate rules
    const isDuplicate = this.rules.some(r => {
      if (r.type !== rule.type) return false;
      if (r.type === 'tool' && rule.type === 'tool') return r.toolName === rule.toolName;
      return true;
    });
    if (!isDuplicate) {
      this.rules.push(rule);
    }
  }

  clearRules(): void {
    this.rules = [];
  }

  getRules(): readonly ApprovalScope[] {
    return this.rules;
  }

  shouldAutoApprove(toolName: string, category: string): boolean {
    return this.rules.some(rule => {
      switch (rule.type) {
        case 'all': return true;
        case 'read': return category === 'read';
        case 'write': return category === 'write';
        case 'tool': return rule.toolName === toolName;
        default: return false;
      }
    });
  }
}
