import { Component, OnInit, signal, computed, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, ActivatedRoute } from '@angular/router';
import { ExecutionService } from '../../core/services';
import { Execution } from '../../core/models';

interface MetricTab {
  key: string;
  label: string;
  subtitle: string;
}

interface DatePreset {
  label: string;
  days: number;
  locked: boolean;
}

interface CalendarDay {
  date: Date;
  day: number;
  outsideMonth: boolean;
  disabled: boolean;
  selected: boolean;
  selectionStart: boolean;
  selectionEnd: boolean;
  today: boolean;
  value: string; // YYYY-MM-DD
}

@Component({
  selector: 'app-insights',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './insights.component.html',
  styleUrl: './insights.component.scss'
})
export class InsightsComponent implements OnInit {
  activeMetric = signal('total');
  executions = signal<Execution[]>([]);
  showDatePicker = signal(false);

  // Date range state
  selectedPreset = signal('Last 7 days');
  rangeStart = signal(this.daysAgo(7));
  rangeEnd = signal(this.today());
  calendarMonth = signal(new Date().getMonth());
  calendarYear = signal(new Date().getFullYear());

  presets: DatePreset[] = [
    { label: 'Last 24 hours', days: 1, locked: false },
    { label: 'Last 7 days', days: 7, locked: false },
    { label: 'Last 14 days', days: 14, locked: false },
    { label: 'Last 30 days', days: 30, locked: false },
    { label: 'Last 90 days', days: 90, locked: false },
    { label: '6 months', days: 180, locked: false },
    { label: 'One year', days: 365, locked: false }
  ];

  tabs: MetricTab[] = [
    { key: 'total', label: 'Prod. executions', subtitle: 'Last 7 days' },
    { key: 'failed', label: 'Failed prod. executions', subtitle: 'Last 7 days' },
    { key: 'failureRate', label: 'Failure rate', subtitle: 'Last 7 days' },
    { key: 'averageRunTime', label: 'Run time (avg.)', subtitle: 'Last 7 days' }
  ];

  totalExecutions = computed(() => {
    const execs = this.executions();
    return Array.isArray(execs) ? execs.filter(e => e.mode !== 'manual').length : 0;
  });

  failedExecutions = computed(() => {
    const execs = this.executions();
    return Array.isArray(execs) ? execs.filter(e => e.status === 'error' && e.mode !== 'manual').length : 0;
  });

  failureRate = computed(() => {
    const total = this.totalExecutions();
    if (total === 0) return 0;
    return Math.round((this.failedExecutions() / total) * 100);
  });

  avgRunTime = computed(() => {
    const execs = this.executions();
    if (!Array.isArray(execs)) return 0;
    const finished = execs.filter(e => e.startedAt && e.finishedAt);
    if (finished.length === 0) return 0;
    const totalMs = finished.reduce((sum, e) => {
      return sum + (new Date(e.finishedAt!).getTime() - new Date(e.startedAt!).getTime());
    }, 0);
    return Math.round(totalMs / finished.length / 1000);
  });

  dateRangeLabel = computed(() => {
    const start = this.rangeStart();
    const end = this.rangeEnd();
    const fmt = (d: Date) => d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
    return `${fmt(start)} - ${fmt(end)}`;
  });

  calendarMonthLabel = computed(() => {
    const d = new Date(this.calendarYear(), this.calendarMonth(), 1);
    return d.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
  });

  calendarWeeks = computed(() => {
    const year = this.calendarYear();
    const month = this.calendarMonth();
    const start = this.rangeStart();
    const end = this.rangeEnd();
    const todayStr = this.toDateString(this.today());

    // First day of month, find the Monday of the week it falls on
    const firstOfMonth = new Date(year, month, 1);
    const dayOfWeek = firstOfMonth.getDay(); // 0=Sun
    const mondayOffset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
    const calStart = new Date(year, month, 1 + mondayOffset);

    const weeks: CalendarDay[][] = [];
    const cursor = new Date(calStart);

    for (let w = 0; w < 6; w++) {
      const week: CalendarDay[] = [];
      for (let d = 0; d < 7; d++) {
        const dateStr = this.toDateString(cursor);
        const startStr = this.toDateString(start);
        const endStr = this.toDateString(end);
        const isOutside = cursor.getMonth() !== month;
        const isAfterToday = dateStr > todayStr;
        const isBeforeRange = dateStr < startStr;

        week.push({
          date: new Date(cursor),
          day: cursor.getDate(),
          outsideMonth: isOutside,
          disabled: isOutside || isAfterToday || isBeforeRange,
          selected: dateStr >= startStr && dateStr <= endStr,
          selectionStart: dateStr === startStr,
          selectionEnd: dateStr === endStr,
          today: dateStr === todayStr,
          value: dateStr
        });
        cursor.setDate(cursor.getDate() + 1);
      }
      weeks.push(week);
      // Stop if we've gone past the month
      if (cursor.getMonth() !== month && cursor.getDate() > 7) break;
    }
    return weeks;
  });

  dateFieldStart = computed(() => this.formatDateField(this.rangeStart()));
  dateFieldEnd = computed(() => this.formatDateField(this.rangeEnd()));

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private executionService: ExecutionService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const metric = params.get('metric') || 'total';
      this.activeMetric.set(metric);
    });
    this.loadExecutions();
  }

  loadExecutions(): void {
    this.executionService.list().subscribe({
      next: (data) => this.executions.set(Array.isArray(data) ? data : [])
    });
  }

  getTabValue(key: string): string {
    switch (key) {
      case 'total': return String(this.totalExecutions());
      case 'failed': return String(this.failedExecutions());
      case 'failureRate': return `${this.failureRate()}%`;
      case 'averageRunTime': return this.formatSeconds(this.avgRunTime());
      default: return '0';
    }
  }

  formatSeconds(s: number): string {
    if (s === 0) return '0s';
    if (s < 60) return `${s}s`;
    if (s < 3600) return `${Math.floor(s / 60)}m ${s % 60}s`;
    return `${Math.floor(s / 3600)}h ${Math.floor((s % 3600) / 60)}m`;
  }

  toggleDatePicker(event: Event): void {
    event.stopPropagation();
    this.showDatePicker.update(v => !v);
  }

  closeDatePicker(): void {
    this.showDatePicker.set(false);
  }

  selectPreset(preset: DatePreset, event: Event): void {
    event.stopPropagation();
    this.selectedPreset.set(preset.label);
    this.rangeStart.set(this.daysAgo(preset.days));
    this.rangeEnd.set(this.today());
    // Reset calendar to current month
    const now = new Date();
    this.calendarMonth.set(now.getMonth());
    this.calendarYear.set(now.getFullYear());
    this.showDatePicker.set(false);
  }

  onDatePickerClick(event: Event): void {
    event.stopPropagation();
  }

  prevMonth(): void {
    let m = this.calendarMonth();
    let y = this.calendarYear();
    if (m === 0) {
      this.calendarMonth.set(11);
      this.calendarYear.set(y - 1);
    } else {
      this.calendarMonth.set(m - 1);
    }
  }

  nextMonth(): void {
    let m = this.calendarMonth();
    let y = this.calendarYear();
    if (m === 11) {
      this.calendarMonth.set(0);
      this.calendarYear.set(y + 1);
    } else {
      this.calendarMonth.set(m + 1);
    }
  }

  canGoPrev(): boolean {
    // Allow going back up to 1 year
    const now = new Date();
    const oneYearAgo = new Date(now.getFullYear() - 1, now.getMonth(), 1);
    const current = new Date(this.calendarYear(), this.calendarMonth(), 1);
    return current > oneYearAgo;
  }

  canGoNext(): boolean {
    const now = new Date();
    return this.calendarYear() < now.getFullYear() ||
      (this.calendarYear() === now.getFullYear() && this.calendarMonth() < now.getMonth());
  }

  private today(): Date {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), d.getDate());
  }

  private daysAgo(n: number): Date {
    const d = this.today();
    d.setDate(d.getDate() - n + 1);
    return d;
  }

  private toDateString(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private formatDateField(d: Date): string {
    return `${d.getMonth() + 1}/${d.getDate()}/${d.getFullYear()}`;
  }
}
