import { Component, OnInit, OnDestroy, signal, computed, HostListener, ViewChild, ElementRef, AfterViewInit, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, ActivatedRoute } from '@angular/router';
import { ExecutionService } from '../../core/services';
import { Execution } from '../../core/models';
import {
  Chart,
  BarController,
  BarElement,
  LineController,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Filler
} from 'chart.js';

Chart.register(
  BarController, BarElement,
  LineController, LineElement, PointElement,
  CategoryScale, LinearScale,
  Tooltip, Filler
);

interface MetricTab {
  key: string;
  label: string;
}

interface DatePreset {
  label: string;
  rollingMinutes?: number;
  days?: number;
  yesterday?: boolean;
  customRange?: boolean;
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
  value: string;
}

interface TimeBucket {
  label: string;
  start: Date;
  end: Date;
  total: number;
  success: number;
  failed: number;
  failureRate: number;
  totalDurationMs: number;
  finishedCount: number;
  avgRunTimeMs: number;
}


@Component({
  selector: 'app-insights',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './insights.component.html',
  styleUrl: './insights.component.scss'
})
export class InsightsComponent implements OnInit, OnDestroy, AfterViewInit {
  @ViewChild('chartCanvas') chartCanvas!: ElementRef<HTMLCanvasElement>;

  activeMetric = signal('total');
  executions = signal<Execution[]>([]);
  showDatePicker = signal(false);

  selectedPreset = signal('Last 7 days');
  rangeStart = signal(this.daysAgo(7));
  rangeEnd = signal(this.today());
  rollingHours = signal(false);
  customRangeActive = signal(false);
  customRangeStep = signal<'start' | 'end'>('start');
  calendarMonth = signal(new Date().getMonth());
  calendarYear = signal(new Date().getFullYear());

  private chart: Chart | null = null;
  private chartReady = false;

  presets: DatePreset[] = [
    { label: 'Last 30 minutes', rollingMinutes: 30, locked: false },
    { label: 'Last 60 minutes', rollingMinutes: 60, locked: false },
    { label: 'Last 2 hours', rollingMinutes: 120, locked: false },
    { label: 'Last 4 hours', rollingMinutes: 240, locked: false },
    { label: 'Last 8 hours', rollingMinutes: 480, locked: false },
    { label: 'Last 24 hours', rollingMinutes: 1440, locked: false },
    { label: 'Yesterday', yesterday: true, locked: false },
    { label: 'Last 7 days', days: 7, locked: false },
    { label: 'Last 14 days', days: 14, locked: false },
    { label: 'Last 30 days', days: 30, locked: false },
    { label: 'Custom range', customRange: true, locked: false },
  ];

  tabs: MetricTab[] = [
    { key: 'total', label: 'Total executions' },
    { key: 'failed', label: 'Failed executions' },
    { key: 'failureRate', label: 'Failure rate' },
    { key: 'averageRunTime', label: 'Run time (avg.)' }
  ];

  // Filtered executions within the selected date range
  filteredExecutions = computed(() => {
    const execs = this.executions();
    if (!Array.isArray(execs)) return [];
    const start = this.rangeStart().getTime();
    const end = this.rollingHours()
      ? this.rangeEnd().getTime()
      : this.rangeEnd().getTime() + 86400000 - 1; // include full end day
    return execs.filter(e => {
      if (!e.startedAt) return false;
      const t = new Date(e.startedAt).getTime();
      return t >= start && t <= end;
    });
  });

  totalExecutions = computed(() => {
    return this.filteredExecutions().length;
  });

  failedExecutions = computed(() => {
    return this.filteredExecutions().filter(e => e.status?.toLowerCase() === 'error').length;
  });

  failureRate = computed(() => {
    const total = this.totalExecutions();
    if (total === 0) return 0;
    return Math.round((this.failedExecutions() / total) * 100);
  });

  avgRunTimeMs = computed(() => {
    const execs = this.filteredExecutions();
    const finished = execs.filter(e => e.startedAt && e.finishedAt);
    if (finished.length === 0) return 0;
    const totalMs = finished.reduce((sum, e) => {
      return sum + (new Date(e.finishedAt!).getTime() - new Date(e.startedAt!).getTime());
    }, 0);
    return Math.round(totalMs / finished.length);
  });

  timeBuckets = computed<TimeBucket[]>(() => {
    const start = this.rangeStart();
    const end = this.rangeEnd();
    const rolling = this.rollingHours();
    const execs = this.filteredExecutions();

    const buckets = this.buildBuckets(start, end, rolling);

    for (const exec of execs) {
      if (!exec.startedAt) continue;
      const execTime = new Date(exec.startedAt).getTime();
      const bucket = buckets.find(b => execTime >= b.start.getTime() && execTime < b.end.getTime());
      if (!bucket) continue;

      bucket.total++;
      if (exec.status?.toLowerCase() === 'error') {
        bucket.failed++;
      } else {
        bucket.success++;
      }

      if (exec.startedAt && exec.finishedAt) {
        const duration = new Date(exec.finishedAt).getTime() - new Date(exec.startedAt).getTime();
        bucket.totalDurationMs += duration;
        bucket.finishedCount++;
      }
    }

    for (const bucket of buckets) {
      bucket.failureRate = bucket.total > 0 ? Math.round((bucket.failed / bucket.total) * 100) : 0;
      bucket.avgRunTimeMs = bucket.finishedCount > 0 ? Math.round(bucket.totalDurationMs / bucket.finishedCount) : 0;
    }

    return buckets;
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

    const firstOfMonth = new Date(year, month, 1);
    const dayOfWeek = firstOfMonth.getDay();
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

        week.push({
          date: new Date(cursor),
          day: cursor.getDate(),
          outsideMonth: isOutside,
          disabled: isOutside || isAfterToday,
          selected: dateStr >= startStr && dateStr <= endStr,
          selectionStart: dateStr === startStr,
          selectionEnd: dateStr === endStr,
          today: dateStr === todayStr,
          value: dateStr
        });
        cursor.setDate(cursor.getDate() + 1);
      }
      weeks.push(week);
      if (cursor.getMonth() !== month && cursor.getDate() > 7) break;
    }
    return weeks;
  });

  dateFieldStart = computed(() => this.formatDateField(this.rangeStart()));
  dateFieldEnd = computed(() => this.formatDateField(this.rangeEnd()));

  private chartEffect = effect(() => {
    // Re-read reactive dependencies
    const metric = this.activeMetric();
    const buckets = this.timeBuckets();
    // Render chart when data changes
    if (this.chartReady) {
      this.renderChart(metric, buckets);
    }
  });

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

  ngAfterViewInit(): void {
    this.chartReady = true;
    this.renderChart(this.activeMetric(), this.timeBuckets());
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  loadExecutions(): void {
    this.executionService.list({ size: '10000' }).subscribe({
      next: (data) => this.executions.set(Array.isArray(data) ? data : [])
    });
  }

  getTabValue(key: string): string {
    switch (key) {
      case 'total': return String(this.totalExecutions());
      case 'failed': return String(this.failedExecutions());
      case 'failureRate': return `${this.failureRate()}%`;
      case 'averageRunTime': return this.formatMs(this.avgRunTimeMs());
      default: return '0';
    }
  }

  formatMs(ms: number): string {
    if (ms === 0) return '0ms';
    if (ms < 1000) return `${ms}ms`;
    const s = ms / 1000;
    if (s < 60) return `${s.toFixed(1)}s`;
    const min = Math.floor(s / 60);
    const sec = Math.round(s % 60);
    if (min < 60) return `${min}m ${sec}s`;
    const hr = Math.floor(min / 60);
    const remMin = min % 60;
    return `${hr}h ${remMin}m`;
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

    if (preset.customRange) {
      this.customRangeActive.set(true);
      this.customRangeStep.set('start');
      return; // keep picker open for day selection
    }

    this.customRangeActive.set(false);

    if (preset.rollingMinutes != null) {
      const now = new Date();
      this.rangeStart.set(new Date(now.getTime() - preset.rollingMinutes * 60000));
      this.rangeEnd.set(now);
      this.rollingHours.set(true);
    } else if (preset.yesterday) {
      const yesterday = this.today();
      yesterday.setDate(yesterday.getDate() - 1);
      this.rangeStart.set(yesterday);
      this.rangeEnd.set(yesterday);
      this.rollingHours.set(false);
    } else if (preset.days != null) {
      this.rangeStart.set(this.daysAgo(preset.days));
      this.rangeEnd.set(this.today());
      this.rollingHours.set(false);
    }

    const now = new Date();
    this.calendarMonth.set(now.getMonth());
    this.calendarYear.set(now.getFullYear());
    this.showDatePicker.set(false);
  }

  onCalendarDayClick(day: CalendarDay): void {
    if (!this.customRangeActive() || day.outsideMonth || day.disabled) return;

    if (this.customRangeStep() === 'start') {
      this.rangeStart.set(day.date);
      this.rangeEnd.set(day.date);
      this.rollingHours.set(false);
      this.customRangeStep.set('end');
    } else {
      if (day.date >= this.rangeStart()) {
        this.rangeEnd.set(day.date);
      } else {
        this.rangeEnd.set(new Date(this.rangeStart()));
        this.rangeStart.set(day.date);
      }
      this.customRangeStep.set('start');
    }
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

  // --- Chart rendering ---

  private renderChart(metric: string, buckets: TimeBucket[]): void {
    if (!this.chartCanvas) return;
    const ctx = this.chartCanvas.nativeElement.getContext('2d');
    if (!ctx) return;

    this.chart?.destroy();

    const labels = buckets.map(b => b.label);

    switch (metric) {
      case 'total':
        this.chart = this.buildTotalChart(ctx, labels, buckets);
        break;
      case 'failed':
        this.chart = this.buildFailedChart(ctx, labels, buckets);
        break;
      case 'failureRate':
        this.chart = this.buildFailureRateChart(ctx, labels, buckets);
        break;
      case 'averageRunTime':
        this.chart = this.buildRunTimeChart(ctx, labels, buckets);
        break;
    }
  }

  private buildTotalChart(ctx: CanvasRenderingContext2D, labels: string[], buckets: TimeBucket[]): Chart {
    return new Chart(ctx, {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            label: 'Success',
            data: buckets.map(b => b.success),
            borderColor: 'hsl(147, 60%, 45%)',
            backgroundColor: 'hsla(147, 60%, 40%, 0.1)',
            borderWidth: 2,
            pointRadius: 3,
            pointBackgroundColor: 'hsl(147, 60%, 45%)',
            pointBorderColor: 'hsl(147, 60%, 45%)',
            fill: true,
            tension: 0.3
          },
          {
            label: 'Failed',
            data: buckets.map(b => b.failed),
            borderColor: 'hsl(355, 83%, 52%)',
            backgroundColor: 'hsla(355, 83%, 52%, 0.1)',
            borderWidth: 2,
            pointRadius: 3,
            pointBackgroundColor: 'hsl(355, 83%, 52%)',
            pointBorderColor: 'hsl(355, 83%, 52%)',
            fill: true,
            tension: 0.3
          }
        ]
      },
      options: this.lineOptions('Executions')
    });
  }

  private buildFailedChart(ctx: CanvasRenderingContext2D, labels: string[], buckets: TimeBucket[]): Chart {
    return new Chart(ctx, {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: 'Failed',
          data: buckets.map(b => b.failed),
          borderColor: 'hsl(355, 83%, 52%)',
          backgroundColor: 'hsla(355, 83%, 52%, 0.1)',
          borderWidth: 2,
          pointRadius: 3,
          pointBackgroundColor: 'hsl(355, 83%, 52%)',
          pointBorderColor: 'hsl(355, 83%, 52%)',
          fill: true,
          tension: 0.3
        }]
      },
      options: this.lineOptions('Failed executions')
    });
  }

  private buildFailureRateChart(ctx: CanvasRenderingContext2D, labels: string[], buckets: TimeBucket[]): Chart {
    return new Chart(ctx, {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          label: 'Failure rate',
          data: buckets.map(b => b.failureRate),
          backgroundColor: 'hsl(36, 77%, 50%)',
          borderRadius: 3,
          maxBarThickness: 32
        }]
      },
      options: this.barOptions(false, 'Failure rate (%)')
    });
  }

  private buildRunTimeChart(ctx: CanvasRenderingContext2D, labels: string[], buckets: TimeBucket[]): Chart {
    return new Chart(ctx, {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: 'Avg. run time',
          data: buckets.map(b => b.avgRunTimeMs),
          borderColor: 'hsl(247, 49%, 53%)',
          backgroundColor: 'hsla(247, 49%, 53%, 0.1)',
          borderWidth: 2,
          pointRadius: 3,
          pointBackgroundColor: 'hsl(247, 49%, 53%)',
          pointBorderColor: 'hsl(247, 49%, 53%)',
          fill: true,
          tension: 0.3
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        interaction: {
          mode: 'index' as const,
          intersect: false
        },
        plugins: {
          tooltip: {
            backgroundColor: 'hsl(0, 0%, 17%)',
            titleColor: 'hsl(0, 0%, 90%)',
            bodyColor: 'hsl(0, 0%, 75%)',
            borderColor: 'hsl(0, 0%, 28%)',
            borderWidth: 1,
            padding: 10,
            cornerRadius: 6,
            callbacks: {
              label: (context: any) => {
                const val = context.parsed.y;
                return `  ${context.dataset.label}: ${this.formatMs(val)}`;
              }
            }
          }
        },
        scales: {
          x: {
            grid: { color: 'hsl(0, 0%, 17%)', drawTicks: false },
            ticks: { color: 'hsl(0, 0%, 50%)', font: { size: 11 }, maxRotation: 45, padding: 8 },
            border: { display: false }
          },
          y: {
            beginAtZero: true,
            grid: { color: 'hsl(0, 0%, 17%)', drawTicks: false },
            ticks: {
              color: 'hsl(0, 0%, 50%)',
              font: { size: 11 },
              padding: 8,
              callback: (value: any) => this.formatMs(Number(value))
            },
            border: { display: false },
            title: {
              display: true,
              text: 'Avg. run time',
              color: 'hsl(0, 0%, 45%)',
              font: { size: 11 }
            }
          }
        }
      }
    });
  }

  private barOptions(stacked: boolean, yTitle: string): any {
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      interaction: {
        mode: 'index' as const,
        intersect: false
      },
      plugins: {
        tooltip: {
          backgroundColor: 'hsl(0, 0%, 17%)',
          titleColor: 'hsl(0, 0%, 90%)',
          bodyColor: 'hsl(0, 0%, 75%)',
          borderColor: 'hsl(0, 0%, 28%)',
          borderWidth: 1,
          padding: 10,
          cornerRadius: 6
        }
      },
      scales: {
        x: {
          stacked,
          grid: { color: 'hsl(0, 0%, 17%)', drawTicks: false },
          ticks: { color: 'hsl(0, 0%, 50%)', font: { size: 11 }, maxRotation: 45, padding: 8 },
          border: { display: false }
        },
        y: {
          stacked,
          beginAtZero: true,
          grid: { color: 'hsl(0, 0%, 17%)', drawTicks: false },
          ticks: { color: 'hsl(0, 0%, 50%)', font: { size: 11 }, padding: 8, precision: 0 },
          border: { display: false },
          title: {
            display: true,
            text: yTitle,
            color: 'hsl(0, 0%, 45%)',
            font: { size: 11 }
          }
        }
      }
    };
  }

  private lineOptions(yTitle: string): any {
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      interaction: {
        mode: 'index' as const,
        intersect: false
      },
      plugins: {
        tooltip: {
          backgroundColor: 'hsl(0, 0%, 17%)',
          titleColor: 'hsl(0, 0%, 90%)',
          bodyColor: 'hsl(0, 0%, 75%)',
          borderColor: 'hsl(0, 0%, 28%)',
          borderWidth: 1,
          padding: 10,
          cornerRadius: 6
        }
      },
      scales: {
        x: {
          grid: { color: 'hsl(0, 0%, 17%)', drawTicks: false },
          ticks: { color: 'hsl(0, 0%, 50%)', font: { size: 11 }, maxRotation: 45, padding: 8 },
          border: { display: false }
        },
        y: {
          beginAtZero: true,
          grid: { color: 'hsl(0, 0%, 17%)', drawTicks: false },
          ticks: { color: 'hsl(0, 0%, 50%)', font: { size: 11 }, padding: 8, precision: 0 },
          border: { display: false },
          title: {
            display: true,
            text: yTitle,
            color: 'hsl(0, 0%, 45%)',
            font: { size: 11 }
          }
        }
      }
    };
  }

  // --- Bucket building ---

  private buildBuckets(start: Date, end: Date, rolling: boolean): TimeBucket[] {
    const endBoundary = rolling
      ? end.getTime()
      : end.getTime() + 86400000; // extend to cover the full end day
    const totalHours = (endBoundary - start.getTime()) / (1000 * 60 * 60);

    let stepMs: number;
    let labelFn: (d: Date) => string;
    const timeFmt = (d: Date) => d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const dateFmt = (d: Date) => d.toLocaleDateString([], { month: 'short', day: 'numeric' });

    if (totalHours <= 2) {
      stepMs = 5 * 60 * 1000;      // 5-minute buckets
      labelFn = timeFmt;
    } else if (totalHours <= 8) {
      stepMs = 30 * 60 * 1000;     // 30-minute buckets
      labelFn = timeFmt;
    } else if (totalHours <= 48) {
      stepMs = 60 * 60 * 1000;     // hourly buckets
      labelFn = timeFmt;
    } else if (totalHours <= 30 * 24) {
      stepMs = 24 * 60 * 60 * 1000; // daily buckets
      labelFn = dateFmt;
    } else {
      stepMs = 7 * 24 * 60 * 60 * 1000; // weekly buckets
      labelFn = dateFmt;
    }

    const buckets: TimeBucket[] = [];
    let cursor = start.getTime();

    while (cursor < endBoundary) {
      buckets.push({
        label: labelFn(new Date(cursor)),
        start: new Date(cursor),
        end: new Date(cursor + stepMs),
        total: 0,
        success: 0,
        failed: 0,
        failureRate: 0,
        totalDurationMs: 0,
        finishedCount: 0,
        avgRunTimeMs: 0
      });
      cursor += stepMs;
    }

    return buckets;
  }

  // --- Helpers ---

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
