import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';

/**
 * Saves a file that came from the API (e.g. the PDF of a report): a temporary object URL and a link with `download`,
 * clicked and removed. The URL is revoked right after, so the content does not stay in memory.
 */
@Injectable({ providedIn: 'root' })
export class FileDownload {
  private readonly document = inject(DOCUMENT);

  save(content: Blob, fileName: string): void {
    const url = URL.createObjectURL(content);
    const link = this.document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.style.display = 'none';
    this.document.body.appendChild(link);
    try {
      link.click();
    } finally {
      link.remove();
      URL.revokeObjectURL(url);
    }
  }
}
