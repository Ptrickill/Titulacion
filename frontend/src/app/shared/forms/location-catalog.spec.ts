import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FAKE_LOCATIONS } from '../people/patient-testing';
import { formatAddress, LocationCatalogApi, placeOf } from './location-catalog';

describe('LocationCatalogApi (MEJ-03)', () => {
  let api: LocationCatalogApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(LocationCatalogApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('asks for the catalog once and shares it with every form', () => {
    const received: string[] = [];
    api.catalog().subscribe((catalog) => received.push(catalog.version));
    api.catalog().subscribe((catalog) => received.push(catalog.version));
    backend.expectOne('/api/catalogs/locations').flush(FAKE_LOCATIONS);
    api.catalog().subscribe((catalog) => received.push(catalog.version));

    backend.expectNone('/api/catalogs/locations');
    expect(received).toEqual(['CGE 2025', 'CGE 2025', 'CGE 2025']);
  });

  it('does not keep a failed request, so the next form tries again', () => {
    api.catalog().subscribe({ error: () => undefined });
    backend.expectOne('/api/catalogs/locations').flush(null, { status: 500, statusText: 'Error' });

    api.catalog().subscribe();
    backend.expectOne('/api/catalogs/locations').flush(FAKE_LOCATIONS);
  });
});

describe('formatAddress', () => {
  const address = {
    provinceCode: '17',
    cantonCode: '1701',
    parishCode: '170156',
    mainStreet: ' Av. Ilaló ',
    streetNumber: 'N12-34',
    crossStreet: 'Río Pita',
    reference: 'Frente al parque central',
  };

  it('composes the address as the backend does, with the display names of the catalog', () => {
    expect(formatAddress(FAKE_LOCATIONS, address)).toBe(
      'Av. Ilaló N12-34 y Río Pita, Conocoto, Distrito Metropolitano de Quito, Pichincha. ' +
        'Referencia: Frente al parque central',
    );
    expect(formatAddress(FAKE_LOCATIONS, { ...address, streetNumber: null, reference: '' })).toBe(
      'Av. Ilaló y Río Pita, Conocoto, Distrito Metropolitano de Quito, Pichincha',
    );
  });

  it('finds no place for codes that do not belong together', () => {
    expect(placeOf(FAKE_LOCATIONS, { ...address, cantonCode: '0901' }).parish).toBeUndefined();
  });
});
