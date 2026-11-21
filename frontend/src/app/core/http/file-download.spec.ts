import { TestBed } from '@angular/core/testing';
import { FileDownload } from './file-download';

describe('FileDownload', () => {
  // jsdom has no object URLs: they are replaced during each test and restored after
  const { createObjectURL, revokeObjectURL } = URL;

  afterEach(() => {
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = revokeObjectURL;
    vi.restoreAllMocks();
  });

  it('clicks a temporary link with the name of the file and frees the URL', () => {
    const create = vi.fn(() => 'blob:informe');
    const revoke = vi.fn();
    URL.createObjectURL = create;
    URL.revokeObjectURL = revoke;
    const clicked: HTMLAnchorElement[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      clicked.push(this);
    });
    const pdf = new Blob(['%PDF'], { type: 'application/pdf' });

    TestBed.inject(FileDownload).save(pdf, 'informe-operativo-2026-11-16.pdf');

    expect(create).toHaveBeenCalledWith(pdf);
    expect(clicked).toHaveLength(1);
    expect(clicked[0].download).toBe('informe-operativo-2026-11-16.pdf');
    expect(clicked[0].href).toBe('blob:informe');
    expect(clicked[0].isConnected).toBe(false);
    expect(revoke).toHaveBeenCalledWith('blob:informe');
  });
});
