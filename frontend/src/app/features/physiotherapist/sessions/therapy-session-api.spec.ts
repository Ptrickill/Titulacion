import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { painScoreLabel } from './session-labels';
import { fakeSession, fakeTimeline, fakeVersions, pageOf } from './session-testing';
import { TherapySessionApi, TherapySessionRequest } from './therapy-session-api';

describe('TherapySessionApi', () => {
  let api: TherapySessionApi;
  let backend: HttpTestingController;

  const request: TherapySessionRequest = {
    sessionDate: '2026-11-04',
    sessionTime: '09:30',
    sessionType: 'Fisioterapia lumbar mecánica',
    activitiesPerformed: 'Movilización lumbar',
    clinicalObservations: 'Buena tolerancia',
    patientInstructions: null,
    painScore: 4,
    progressStatus: 'IMPROVING',
    status: 'COMPLETED',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(TherapySessionApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists the sessions of a treatment page by page', () => {
    api.list(30, 2, 10).subscribe((page) => expect(page.totalElements).toBe(21));

    const call = backend.expectOne((r) => r.url === '/api/treatments/30/therapy-sessions');
    expect(call.request.method).toBe('GET');
    expect(call.request.params.get('page')).toBe('2');
    expect(call.request.params.get('size')).toBe('10');
    call.flush(pageOf([fakeSession(90)], 21, 2));
  });

  it('registers a session on the treatment and corrects it by its id', () => {
    api.register(30, request).subscribe();
    api.correct(90, request).subscribe();

    const register = backend.expectOne('/api/treatments/30/therapy-sessions');
    expect(register.request.method).toBe('POST');
    expect(register.request.body).toEqual(request);
    register.flush(fakeSession(90));
    const correct = backend.expectOne('/api/therapy-sessions/90');
    expect(correct.request.method).toBe('PUT');
    correct.flush(fakeSession(90));
  });

  it('reads a session and its versions', () => {
    api.find(90).subscribe((session) => expect(session.sequenceNumber).toBe(1));
    api.versions(90).subscribe((versions) => expect(versions).toHaveLength(2));

    backend.expectOne('/api/therapy-sessions/90').flush(fakeSession(90));
    backend.expectOne('/api/therapy-sessions/90/versions').flush(fakeVersions());
  });

  it('reads the timeline of a treatment without a range', () => {
    api
      .timeline(30, { from: null, to: null })
      .subscribe((timeline) => expect(timeline.painCurve).toHaveLength(3));

    const call = backend.expectOne((r) => r.url === '/api/treatments/30/timeline');
    expect(call.request.method).toBe('GET');
    expect(call.request.params.keys()).toEqual([]);
    call.flush(fakeTimeline());
  });

  it('sends only the bounds of the range that are set', () => {
    api.timeline(30, { from: '2026-10-05', to: '2026-10-31' }).subscribe();
    api.timeline(30, { from: null, to: '2026-10-31' }).subscribe();

    const [both, onlyTo] = backend.match((r) => r.url === '/api/treatments/30/timeline');
    expect(both.request.params.get('from')).toBe('2026-10-05');
    expect(both.request.params.get('to')).toBe('2026-10-31');
    expect(onlyTo.request.params.has('from')).toBe(false);
    expect(onlyTo.request.params.get('to')).toBe('2026-10-31');
    both.flush(fakeTimeline());
    onlyTo.flush(fakeTimeline());
  });

  it('names the intensity of each EVA value as prototype 06', () => {
    expect([0, 2, 4, 6, 7, 10].map(painScoreLabel)).toEqual([
      'Sin dolor',
      'Dolor leve',
      'Dolor moderado',
      'Dolor moderado',
      'Dolor intenso',
      'Dolor máximo',
    ]);
  });
});
