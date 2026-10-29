import { TestBed } from '@angular/core/testing';
import { SectionAccessNotice } from './section-access-notice';

describe('SectionAccessNotice', () => {
  it('is consumed only once per request', () => {
    const notice = TestBed.inject(SectionAccessNotice);
    expect(notice.consume()).toBe(false);

    notice.request();

    expect(notice.isPending()).toBe(true);
    expect(notice.consume()).toBe(true);
    expect(notice.consume()).toBe(false);
  });
});
