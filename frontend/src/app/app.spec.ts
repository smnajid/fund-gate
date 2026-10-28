import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { provideApi } from './generated/api';

describe('App', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting(), provideApi('')]
    });
  });

  it('opens on the dealer screen', async () => {
    const harness = await RouterTestingHarness.create('/');

    expect(harness.routeNativeElement?.tagName.toLowerCase()).toBe('app-dealer-screen');
  });
});
