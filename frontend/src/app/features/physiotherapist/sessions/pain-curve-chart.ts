import { DatePipe, formatDate } from '@angular/common';
import { Component, computed, ElementRef, inject, input, LOCALE_ID } from '@angular/core';
import {
  CategoryScale,
  ChartConfiguration,
  ChartData,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip,
} from 'chart.js';
import { BaseChartDirective, provideCharts } from 'ng2-charts';
import { painScoreLabel } from './session-labels';
import { PainPoint } from './therapy-session-api';

/** Token values used when the stylesheet is not loaded (e.g. unit tests); same as _tokens.scss. */
const FALLBACK_TOKENS = {
  line: '#0f766e',
  surface: '#ffffff',
  grid: '#e2e8f0',
  text: '#64748b',
  font: "'Plus Jakarta Sans', Roboto, 'Segoe UI', sans-serif",
} as const;

type CurveTokens = Record<keyof typeof FALLBACK_TOKENS, string>;

/**
 * Pain curve of a treatment (HU-11): EVA (0–10) of the initial evaluation followed by each COMPLETED session, as a
 * single line in the teal text token. The initial evaluation is a hollow point, the sessions filled ones; the tooltip
 * names the point, its date and the intensity. A visually hidden table repeats the points for screen readers.
 * Chart.js only registers what a line chart needs.
 */
@Component({
  selector: 'app-pain-curve-chart',
  imports: [BaseChartDirective, DatePipe],
  providers: [
    provideCharts({
      registerables: [
        LineController,
        LineElement,
        PointElement,
        LinearScale,
        CategoryScale,
        Tooltip,
      ],
    }),
  ],
  templateUrl: './pain-curve-chart.html',
  styleUrl: './pain-curve-chart.scss',
})
export class PainCurveChart {
  readonly points = input.required<PainPoint[]>();

  private readonly locale = inject(LOCALE_ID);
  private readonly theme = readTokens(inject(ElementRef<HTMLElement>).nativeElement);
  private readonly font = { family: this.theme.font, size: 12 };

  protected readonly chartData = computed<ChartData<'line', number[], string>>(() => {
    const points = this.points();
    const { line, surface } = this.theme;
    return {
      labels: points.map((point) => point.label),
      datasets: [
        {
          label: 'EVA',
          data: points.map((point) => point.painScore),
          borderColor: line,
          borderWidth: 2,
          tension: 0.25,
          pointRadius: 4,
          pointHoverRadius: 6,
          pointHitRadius: 12,
          pointBorderWidth: 2,
          pointBorderColor: line,
          // The initial evaluation is hollow: it is the starting point, not a session
          pointBackgroundColor: points.map((point) => (point.sessionId === null ? surface : line)),
        },
      ],
    };
  });

  /** Short text alternative of the canvas; the hidden table has every value. */
  protected readonly summary = computed(() => {
    const points = this.points();
    const last = points[points.length - 1];
    return points.length > 1
      ? `Curva de EVA: de ${points[0].painScore}/10 en la evaluación inicial a ${last.painScore}/10 en la ${last.label.toLowerCase()}.`
      : `Curva de EVA: solo la evaluación inicial, ${points[0]?.painScore ?? 0}/10.`;
  });

  protected readonly options: ChartConfiguration<'line', number[], string>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    interaction: { mode: 'index', intersect: false },
    scales: {
      y: {
        min: 0,
        max: 10,
        ticks: { stepSize: 2, color: this.theme.text, font: this.font },
        grid: { color: this.theme.grid },
        border: { display: false },
        title: { display: true, text: 'EVA (0–10)', color: this.theme.text, font: this.font },
      },
      x: {
        ticks: { color: this.theme.text, maxRotation: 0, autoSkip: true, font: this.font },
        grid: { display: false },
      },
    },
    plugins: {
      tooltip: {
        displayColors: false,
        titleFont: { ...this.font, weight: 'bold' },
        bodyFont: this.font,
        callbacks: {
          title: (items) => {
            const point = this.points()[items[0]?.dataIndex ?? 0];
            return point
              ? `${point.label} · ${formatDate(point.date, 'dd/MM/yyyy', this.locale)}`
              : '';
          },
          label: (item) => {
            const painScore = Number(item.raw);
            return `EVA ${painScore}/10 · ${painScoreLabel(painScore)}`;
          },
        },
      },
    },
  };
}

/** Chart.js draws on a canvas, which cannot read CSS variables: the tokens are resolved once. */
function readTokens(element: HTMLElement): CurveTokens {
  const style = getComputedStyle(element);
  const token = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback;
  return {
    line: token('--re-text-teal', FALLBACK_TOKENS.line),
    surface: token('--re-card', FALLBACK_TOKENS.surface),
    grid: token('--re-gray-200', FALLBACK_TOKENS.grid),
    text: token('--re-text-muted', FALLBACK_TOKENS.text),
    font: token('--re-font-sans', FALLBACK_TOKENS.font),
  };
}
