import { Injectable, OnDestroy } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Subject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private client: Client | null = null;
  private subscriptions = new Map<string, any>();
  private connected$ = new Subject<boolean>();
  private reconnectAttempts = 0;
  private readonly maxReconnectAttempts = 10;

  connect(): void {
    if (this.client?.connected) {
      return;
    }

    this.client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.reconnectAttempts = 0;
        this.connected$.next(true);
        console.log('WebSocket connected');
      },
      onDisconnect: () => {
        this.connected$.next(false);
        console.log('WebSocket disconnected');
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message']);
        this.reconnectAttempts++;
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
          console.error('Max reconnect attempts reached');
          this.client?.deactivate();
        }
      }
    });

    this.client.activate();
  }

  subscribe(topic: string, callback: (message: IMessage) => void, onSubscribed?: () => void): void {
    if (!this.client?.connected) {
      this.connect();
      this.connected$.subscribe(connected => {
        if (connected && !this.subscriptions.has(topic)) {
          const sub = this.client!.subscribe(topic, callback);
          this.subscriptions.set(topic, sub);
          onSubscribed?.();
        }
      });
      return;
    }

    if (!this.subscriptions.has(topic)) {
      const sub = this.client.subscribe(topic, callback);
      this.subscriptions.set(topic, sub);
    }
    onSubscribed?.();
  }

  unsubscribe(topic: string): void {
    const sub = this.subscriptions.get(topic);
    if (sub) {
      sub.unsubscribe();
      this.subscriptions.delete(topic);
    }
  }

  send(destination: string, body: any): void {
    if (this.client?.connected) {
      this.client.publish({ destination, body: JSON.stringify(body) });
    }
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
    this.subscriptions.clear();
    this.client?.deactivate();
  }
}
