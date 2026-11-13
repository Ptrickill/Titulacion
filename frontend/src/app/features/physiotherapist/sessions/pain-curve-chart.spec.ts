import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { BaseChartDirective } from 'ng2-charts';
import { PainCurveChart } from './pain-curve-chart';
import { fakePainCurve, stubCanvas } from './session-testing';
import { PainPoint } from './therapy-session-api';

@Component({
  imports: [PainCurveChart],
  template: `<app-pain-curve-chart [points]="points()" />`,
})
class Host {
  readonly points = signal<PainPoint[]>(fakePainCurve());
}

describe('PainCurveChart', () => {
  let restoreCanvas: () => void;

  beforeEach(() => {
    restoreCanvas = stubCanvas();
  });

  afterEach(() => restoreCanvas());

  async function render() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    await fixture.whenStable();
    const chart = fixture.debugElement
      .query(By.directive(BaseChartDirective))
      .injector.get(BaseChartDirective);
    return { fixture, chart, element: fixture.nativeElement as HTMLElement };
  }

  it('draws one line with a point per entry of the curve', async () => {
    const { chart } = await render();

    expect(chart.chart).toBeDefined();
    expect((chart.chart!.config as { type: string }).type).toBe('line');
    expect(chart.chart!.data.labels).toEqual(['Evaluación inicial', 'Sesión 1', 'Sesión 3']);
    expect(chart.chart!.data.datasets).toHaveLength(1);
    expect(chart.chart!.data.datasets[0].data).toEqual([8, 6, 3]);
  });

  it('keeps the EVA axis from 0 to 10 and hides the legend of its single series', async () => {
    const { chart } = await render();

    const options = chart.chart!.options;
    expect(options.scales!['y']!.min).toBe(0);
    expect(options.scales!['y']!.max).toBe(10);
    expect(chart.legend).toBe(false);
  });

  it('draws the initial evaluation as a hollow point', async () => {
    const { chart } = await render();

    const dataset = chart.chart!.data.datasets[0] as { pointBackgroundColor: string[] };
    const [initial, firstSession] = dataset.pointBackgroundColor;
    expect(initial).not.toBe(firstSession);
    expect(initial.toLowerCase()).toBe('#ffffff');
  });

  it('names each point, its date and the intensity in the tooltip', async () => {
    const { chart } = await render();

    const callbacks = chart.chart!.options.plugins!.tooltip!.callbacks!;
    const item = { dataIndex: 2, raw: 3 } as never;
    expect((callbacks.title as (items: never[]) => string)([item])).toBe('Sesión 3 · 08/10/2026');
    expect((callbacks.label as (item: never) => string)(item)).toBe('EVA 3/10 · Dolor leve');
  });

  it('repeats the points in a table for screen readers and describes the canvas', async () => {
    const { element } = await render();

    const rows = Array.from(element.querySelectorAll('tbody tr')).map((row) =>
      Array.from(row.children)
        .map((cell) => cell.textContent!.trim())
        .join(' '),
    );
    expect(rows).toEqual([
      'Evaluación inicial 01/10/2026 8/10',
      'Sesión 1 03/10/2026 6/10',
      'Sesión 3 08/10/2026 3/10',
    ]);
    expect(element.querySelector('canvas')?.getAttribute('aria-label')).toBe(
      'Curva de EVA: de 8/10 en la evaluación inicial a 3/10 en la sesión 3.',
    );
  });

  it('redraws when the points change', async () => {
    const { fixture, chart } = await render();

    fixture.componentInstance.points.set(fakePainCurve().slice(0, 2));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(chart.chart!.data.labels).toEqual(['Evaluación inicial', 'Sesión 1']);
  });
});
