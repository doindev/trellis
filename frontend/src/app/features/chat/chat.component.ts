import { Component, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ChatService } from '../../core/services/chat.service';
import { ChatAgentService } from '../../core/services/chat-agent.service';
import { ChatMessage, ChatAgent } from '../../core/models/chat.model';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.scss'
})
export class ChatComponent implements OnInit, OnDestroy, AfterViewChecked {
  view: string = '';
  sessionId: string = '';
  messages: ChatMessage[] = [];
  messageInput = '';
  sending = false;
  loading = false;

  // Personal agents
  agents: ChatAgent[] = [];
  loadingAgents = false;
  showAgentModal = false;
  editingAgent: ChatAgent | null = null;
  agentForm = { name: '', description: '', systemPrompt: '', icon: '', model: '' };
  savingAgent = false;

  // Icon picker
  showIconPicker = false;
  readonly agentIcons = [
    { icon: '', label: 'Default' },
    { icon: '🤖', label: 'Robot' },
    { icon: '💬', label: 'Chat' },
    { icon: '🧠', label: 'Brain' },
    { icon: '⚡', label: 'Lightning' },
    { icon: '🔧', label: 'Wrench' },
    { icon: '📊', label: 'Chart' },
    { icon: '📝', label: 'Memo' },
    { icon: '🎯', label: 'Target' },
    { icon: '🔍', label: 'Search' },
    { icon: '💡', label: 'Idea' },
    { icon: '🛠️', label: 'Tools' },
    { icon: '📈', label: 'Growth' },
    { icon: '🏗️', label: 'Build' },
    { icon: '🧪', label: 'Science' },
    { icon: '📚', label: 'Books' },
    { icon: '🎨', label: 'Art' },
    { icon: '🌐', label: 'Globe' },
    { icon: '🔐', label: 'Security' },
    { icon: '📧', label: 'Email' },
    { icon: '🗂️', label: 'Files' },
    { icon: '👤', label: 'Person' },
    { icon: '🤝', label: 'Handshake' },
    { icon: '📅', label: 'Calendar' },
  ];

  // Model selector
  showModelDropdown = false;
  modelSearch = '';
  readonly availableModels = [
    { value: 'gpt-4o', label: 'GPT-4o', provider: 'OpenAI' },
    { value: 'gpt-4o-mini', label: 'GPT-4o Mini', provider: 'OpenAI' },
    { value: 'gpt-4.1', label: 'GPT-4.1', provider: 'OpenAI' },
    { value: 'gpt-4.1-mini', label: 'GPT-4.1 Mini', provider: 'OpenAI' },
    { value: 'gpt-4.1-nano', label: 'GPT-4.1 Nano', provider: 'OpenAI' },
    { value: 'o3-mini', label: 'o3-mini', provider: 'OpenAI' },
    { value: 'claude-sonnet-4-5-20250929', label: 'Claude Sonnet 4.5', provider: 'Anthropic' },
    { value: 'claude-haiku-4-5-20251001', label: 'Claude Haiku 4.5', provider: 'Anthropic' },
    { value: 'claude-opus-4-6', label: 'Claude Opus 4.6', provider: 'Anthropic' },
    { value: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro', provider: 'Google' },
    { value: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash', provider: 'Google' },
  ];

  @ViewChild('messagesContainer') messagesContainer!: ElementRef;
  @ViewChild('messageInputEl') messageInputEl!: ElementRef;
  private shouldScrollToBottom = false;

  private routeSub?: Subscription;
  private wsSub?: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private chatService: ChatService,
    private chatAgentService: ChatAgentService
  ) {}

  ngOnInit(): void {
    this.routeSub = this.route.paramMap.subscribe(params => {
      const viewParam = params.get('view');
      if (viewParam === 'personal-agents') {
        this.view = 'personal-agents';
        this.sessionId = '';
        this.chatService.disconnect();
        this.loadAgents();
      } else if (viewParam === 'workflow-agents') {
        this.view = 'workflow-agents';
        this.sessionId = '';
        this.chatService.disconnect();
      } else if (viewParam) {
        // It's a session ID
        this.view = 'conversation';
        this.sessionId = viewParam;
        this.loadMessages();
        this.connectWebSocket();
      } else {
        this.view = 'empty';
        this.sessionId = '';
      }
    });
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
    this.wsSub?.unsubscribe();
    this.chatService.disconnect();
  }

  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  loadMessages(): void {
    this.loading = true;
    this.chatService.getMessages(this.sessionId).subscribe({
      next: (msgs) => {
        this.messages = msgs;
        this.loading = false;
        this.shouldScrollToBottom = true;
      },
      error: () => this.loading = false
    });
  }

  connectWebSocket(): void {
    this.wsSub?.unsubscribe();
    this.chatService.connect(this.sessionId);
    this.wsSub = this.chatService.messages$.subscribe(msg => {
      if (msg.sessionId === this.sessionId) {
        this.messages.push(msg);
        this.shouldScrollToBottom = true;
      }
    });
  }

  onSend(): void {
    const content = this.messageInput.trim();
    if (!content || this.sending) return;

    this.sending = true;
    const userMsg: ChatMessage = {
      id: 'temp-' + Date.now(),
      sessionId: this.sessionId,
      role: 'user',
      content,
      createdAt: new Date().toISOString()
    };
    this.messages.push(userMsg);
    this.messageInput = '';
    this.shouldScrollToBottom = true;

    this.chatService.sendMessage(this.sessionId, content).subscribe({
      next: (saved) => {
        // Replace temp message with saved one
        const idx = this.messages.findIndex(m => m.id === userMsg.id);
        if (idx >= 0) this.messages[idx] = saved;
        this.sending = false;
      },
      error: () => this.sending = false
    });
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.onSend();
    }
  }

  private scrollToBottom(): void {
    try {
      if (this.messagesContainer) {
        this.messagesContainer.nativeElement.scrollTop = this.messagesContainer.nativeElement.scrollHeight;
      }
    } catch (e) {}
  }

  // Markdown rendering (simple regex-based)
  renderMarkdown(text: string): string {
    if (!text) return '';
    let html = this.escapeHtml(text);
    // Code blocks
    html = html.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code class="lang-$1">$2</code></pre>');
    // Inline code
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
    // Bold
    html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    // Italic
    html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');
    // Links
    html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>');
    // Unordered lists
    html = html.replace(/^- (.+)$/gm, '<li>$1</li>');
    html = html.replace(/(<li>.*<\/li>\n?)+/g, '<ul>$&</ul>');
    // Ordered lists
    html = html.replace(/^\d+\. (.+)$/gm, '<li>$1</li>');
    // Newlines (but not inside pre blocks)
    html = html.replace(/(?<!\<\/pre\>)\n/g, '<br>');
    return html;
  }

  private escapeHtml(text: string): string {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  // ── Personal Agents ──

  loadAgents(): void {
    this.loadingAgents = true;
    this.chatAgentService.list().subscribe({
      next: (agents) => {
        this.agents = agents;
        this.loadingAgents = false;
      },
      error: () => this.loadingAgents = false
    });
  }

  onNewAgent(): void {
    this.editingAgent = null;
    this.agentForm = { name: '', description: '', systemPrompt: '', icon: '', model: '' };
    this.showIconPicker = false;
    this.showModelDropdown = false;
    this.showAgentModal = true;
  }

  onEditAgent(agent: ChatAgent): void {
    this.editingAgent = agent;
    this.agentForm = {
      name: agent.name,
      description: agent.description || '',
      systemPrompt: agent.systemPrompt || '',
      icon: agent.icon || '',
      model: agent.model || ''
    };
    this.showIconPicker = false;
    this.showModelDropdown = false;
    this.showAgentModal = true;
  }

  get isAgentFormValid(): boolean {
    return !!(this.agentForm.name.trim() && this.agentForm.systemPrompt.trim() && this.agentForm.model);
  }

  getSelectedModelLabel(): string {
    if (!this.agentForm.model) return '';
    const found = this.availableModels.find(m => m.value === this.agentForm.model);
    return found ? found.label : this.agentForm.model;
  }

  getSelectedModelProvider(): string {
    if (!this.agentForm.model) return '';
    const found = this.availableModels.find(m => m.value === this.agentForm.model);
    return found ? found.provider : '';
  }

  onSelectIcon(icon: string): void {
    this.agentForm.icon = icon;
    this.showIconPicker = false;
  }

  onSelectModel(model: string): void {
    this.agentForm.model = model;
    this.showModelDropdown = false;
  }

  onCreateCredential(provider: string, event: MouseEvent): void {
    event.stopPropagation();
    this.showModelDropdown = false;
    this.showAgentModal = false;
    this.router.navigate(['/home/credentials'], {
      queryParams: { action: 'create-credential', provider: provider.toLowerCase(), t: Date.now() }
    });
  }

  toggleIconPicker(): void {
    this.showIconPicker = !this.showIconPicker;
    this.showModelDropdown = false;
  }

  get filteredModels() {
    if (!this.modelSearch.trim()) return this.availableModels;
    const q = this.modelSearch.toLowerCase();
    return this.availableModels.filter(
      m => m.label.toLowerCase().includes(q) || m.provider.toLowerCase().includes(q) || m.value.toLowerCase().includes(q)
    );
  }

  toggleModelDropdown(): void {
    this.showModelDropdown = !this.showModelDropdown;
    this.showIconPicker = false;
    if (this.showModelDropdown) {
      this.modelSearch = '';
    }
  }

  onSaveAgent(): void {
    if (!this.isAgentFormValid || this.savingAgent) return;
    this.savingAgent = true;

    const payload: Partial<ChatAgent> = {
      name: this.agentForm.name.trim(),
      description: this.agentForm.description.trim() || undefined,
      systemPrompt: this.agentForm.systemPrompt.trim() || undefined,
      icon: this.agentForm.icon || undefined,
      model: this.agentForm.model || undefined
    };

    const obs = this.editingAgent
      ? this.chatAgentService.update(this.editingAgent.id, payload)
      : this.chatAgentService.create(payload);

    obs.subscribe({
      next: () => {
        this.savingAgent = false;
        this.showAgentModal = false;
        this.loadAgents();
      },
      error: () => this.savingAgent = false
    });
  }

  onCloseAgentModal(): void {
    this.showAgentModal = false;
  }

  onDeleteAgent(agent: ChatAgent): void {
    if (!confirm(`Delete agent "${agent.name}"?`)) return;
    this.chatAgentService.delete(agent.id).subscribe({
      next: () => this.loadAgents()
    });
  }

  onChatWithAgent(agent: ChatAgent): void {
    this.chatService.createSession(agent.name, agent.id).subscribe({
      next: (session) => {
        this.router.navigate(['/home/chat', session.id]);
      }
    });
  }
}
