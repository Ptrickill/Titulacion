import { registerLocaleData } from '@angular/common';
import localeEsEc from '@angular/common/locales/es-EC';

/** Locale of every user-facing date and number (Spanish, Ecuador). */
export const APP_LOCALE = 'es-EC';

// Registered on import so that pipes using APP_LOCALE also work in isolated component tests
registerLocaleData(localeEsEc, APP_LOCALE);
