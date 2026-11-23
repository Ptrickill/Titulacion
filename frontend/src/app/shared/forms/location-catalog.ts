import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, Observable, shareReplay, throwError } from 'rxjs';

/** A parish of INEC's catalog (MEJ-03). SEAT: the canton seat of a canton without urban parishes. */
export interface ParishOption {
  code: string;
  /** Official name, as INEC publishes it. */
  name: string;
  /** Name to show, with a capital initial. */
  displayName: string;
  type: 'URBAN' | 'RURAL' | 'SEAT';
}

export interface CantonOption {
  code: string;
  name: string;
  displayName: string;
  parishes: ParishOption[];
}

export interface ProvinceOption {
  code: string;
  name: string;
  displayName: string;
  cantons: CantonOption[];
}

/** GET /api/catalogs/locations: provinces, cantons and parishes of Ecuador. */
export interface LocationCatalog {
  source: string;
  version: string;
  cutoffDate: string;
  provinces: ProvinceOption[];
}

/**
 * The location catalog, requested once per session and shared by every form (MEJ-03). A failed request is not
 * cached, so the next form tries again. The address never leaves the application.
 */
@Injectable({ providedIn: 'root' })
export class LocationCatalogApi {
  private readonly http = inject(HttpClient);
  private cached: Observable<LocationCatalog> | null = null;

  catalog(): Observable<LocationCatalog> {
    if (!this.cached) {
      this.cached = this.http.get<LocationCatalog>('/api/catalogs/locations').pipe(
        catchError((error: unknown) => {
          this.cached = null;
          return throwError(() => error);
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.cached;
  }
}

/** The parts of a structured address, as the API takes them. */
export interface AddressValue {
  provinceCode: string;
  cantonCode: string;
  parishCode: string;
  mainStreet: string;
  streetNumber: string | null;
  crossStreet: string;
  reference: string;
}

/** Province, canton and parish of the codes, or undefined where a code is not in the catalog. */
export function placeOf(
  catalog: LocationCatalog | null | undefined,
  address: Pick<AddressValue, 'provinceCode' | 'cantonCode' | 'parishCode'>,
): { province?: ProvinceOption; canton?: CantonOption; parish?: ParishOption } {
  const province = catalog?.provinces.find((p) => p.code === address.provinceCode);
  const canton = province?.cantons.find((c) => c.code === address.cantonCode);
  const parish = canton?.parishes.find((p) => p.code === address.parishCode);
  return { province, canton, parish };
}

/**
 * The composed address, as the backend writes it: "Av. Ilaló N12-34 y Río Pita, Conocoto, Distrito
 * Metropolitano de Quito, Pichincha. Referencia: frente al parque".
 */
export function formatAddress(
  catalog: LocationCatalog | null | undefined,
  address: AddressValue,
): string {
  const { province, canton, parish } = placeOf(catalog, address);
  const street = [address.mainStreet.trim(), address.streetNumber?.trim()]
    .filter(Boolean)
    .join(' ');
  const parts = [`${street} y ${address.crossStreet.trim()}`];
  if (parish && canton && province) {
    parts.push(parish.displayName, canton.displayName, province.displayName);
  }
  const reference = address.reference.trim();
  return parts.join(', ') + (reference ? `. Referencia: ${reference}` : '');
}
