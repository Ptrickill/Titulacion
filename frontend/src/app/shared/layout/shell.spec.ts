import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthSession } from '../../core/auth/auth-session';
import { Shell } from './shell';

describe('Shell', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: [provideRouter([]), provideHttpClient()],
    }).compileComponents();
    TestBed.inject(AuthSession).startPreviewSession('OPERATIONS_DIRECTOR');
  });

  it('shows the brand, the user role and the role navigation', async () => {
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('.brand')?.textContent).toContain('Rehab Elite');
    expect(element.querySelector('.user-role')?.textContent).toContain('Dirección operativa');
    expect(element.querySelector('a[href="/director"]')).not.toBeNull();
  });

  it('logs out and returns to /login', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.logout')!.click();

    expect(TestBed.inject(AuthSession).isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
