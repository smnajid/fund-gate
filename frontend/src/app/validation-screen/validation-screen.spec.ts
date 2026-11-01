import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CardView, RuleView, provideApi } from '../generated/api';
import { ValidationScreen, highlight } from './validation-screen';

function rule(index: number, overrides: Partial<RuleView>): RuleView {
  return {
    index, kind: 'investor_category', scope: 'SHARE_CLASS', shareClass: 'LU0348927095',
    condition: { kind: 'investor_category', allowed: ['INSTITUTIONAL'], excluded: [] },
    summary: 'I classes: institutional investors only, says the prospectus', status: 'OPEN',
    quoteFoundOnPage: true, noSupportingQuote: false, unsettledContradiction: false,
    citations: [{ document: 'nordea1-prospectus-2026-06', documentType: 'PROSPECTUS', page: 123,
      quote: 'I Institutional investors.', quoteFoundOnPage: true }],
    by: 'Rule Card pipeline', at: '2026-11-02T08:00:00Z',
    ...overrides
  };
}

const DRAFT: CardView = {
  fundName: 'Nordea 1 – Global Climate and Environment Fund', shareClasses: ['LU0348926287', 'LU0348927095'],
  version: 2, status: 'DRAFT', draftedAt: '2026-11-02T08:00:00Z', draftedBy: 'fixture',
  everyQuoteFoundOnPage: true, readyToValidate: false, inForce: false,
  rules: [
    rule(0, { unsettledContradiction: true }),
    rule(1, {
      condition: { kind: 'investor_category', allowed: ['ALL'], excluded: [] },
      summary: 'BI-EUR: suitable for all types of investors, says the KID', unsettledContradiction: true,
      citations: [{ document: 'nordea1-gce-kid-bi-eur', documentType: 'KID', page: 1,
        quote: 'suitable for all types of investors', quoteFoundOnPage: true }]
    })
  ],
  contradictions: [{ kind: 'investor_category', shareClass: 'LU0348927095', rules: [0, 1], settled: false }]
};

describe('highlight', () => {
  it('finds the quote across line breaks and case', () => {
    expect(highlight('Class I\nInstitutional   investors. Yes', 'institutional investors.')).toEqual({
      before: 'Class I\n', quote: 'Institutional   investors.', after: ' Yes'
    });
  });

  it('gives null when the quote is not on the page', () => {
    expect(highlight('All investors', 'Institutional investors')).toBeNull();
  });
});

describe('ValidationScreen', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ValidationScreen],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi('')]
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function openTheDraft() {
    const fixture = TestBed.createComponent(ValidationScreen);
    http.expectOne('/api/rule-cards').flush([DRAFT]);
    http.expectOne('/api/rule-cards/documents/nordea1-prospectus-2026-06/pages/123')
      .flush({ text: 'Class\nI Institutional investors. Yes None' });
    await fixture.whenStable();
    return fixture;
  }

  function button(screen: HTMLElement, label: string): HTMLButtonElement {
    const found = Array.from(screen.querySelectorAll('button')).find(each => each.textContent?.trim() === label);
    if (!found) {
      throw new Error(`No button "${label}"`);
    }
    return found;
  }

  it('leads with the contradiction and keeps Validate card disabled', async () => {
    const screen: HTMLElement = (await openTheDraft()).nativeElement;

    expect(screen.querySelector('.contradictions')?.textContent).toContain('1 contradiction needs a decision');
    expect(screen.querySelector('.contradictions')?.textContent).toContain('KID p.1');
    expect(button(screen, 'Validate card').disabled).toBe(true);
    expect(screen.querySelector('mark')?.textContent).toBe('I Institutional investors.');
  });

  it('shows the checked signals and never a confidence', async () => {
    const screen: HTMLElement = (await openTheDraft()).nativeElement;

    expect(screen.querySelector('.decision')?.textContent).toContain('Quote found on its page');
    expect(screen.textContent?.toLowerCase()).not.toContain('confidence');
  });

  it('follows the prospectus in the validator\'s name', async () => {
    const fixture = await openTheDraft();
    const screen: HTMLElement = fixture.nativeElement;
    const name = screen.querySelector<HTMLInputElement>('#validator')!;
    name.value = 'Cynthia';
    name.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    button(screen, 'Follow prospectus p.123').click();

    const request = http.expectOne('/api/rule-cards/LU0348926287/versions/2/rules/0/follow');
    expect(request.request.body).toEqual({ validator: 'Cynthia' });
  });

  it('asks for the validator\'s name before any step', async () => {
    const fixture = await openTheDraft();
    const screen: HTMLElement = fixture.nativeElement;

    button(screen, 'Accept').click();
    await fixture.whenStable();

    expect(screen.querySelector('[role=alert].failure')?.textContent).toContain('Enter your name first');
  });
});
