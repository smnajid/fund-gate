import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AnswerResponse, provideApi } from '../generated/api';
import { DealerScreen } from './dealer-screen';

const ANNAS_EMAIL = 'Hi desk, can LOC90000101 and LOC90000102 subscribe EUR 80,000 to Nordea Global Climate BI-EUR?';

const ANNAS_ANSWER: AnswerResponse = {
  swaps: [
    { portfolioRef: 'LOC90000101', portfolioId: '3f6c2a10-0000-4000-8000-000000000101' },
    { portfolioRef: 'LOC90000102', portfolioId: '9b1e7c42-0000-4000-8000-000000000102' }
  ],
  checks: [
    {
      checkId: 'c1', portfolioRef: 'LOC90000101', shareClass: 'LU0348927095', clientInitiated: 'UNKNOWN',
      verdict: 'NOT_ELIGIBLE', notices: []
    },
    {
      checkId: 'c2', portfolioRef: 'LOC90000102', shareClass: 'LU0348927095', clientInitiated: 'UNKNOWN',
      verdict: 'ELIGIBLE', notices: []
    }
  ],
  reply: { text: 'Hello,\n\nLOC90000101 cannot subscribe.\nLOC90000102 can subscribe.', draftedByAi: false }
};

describe('DealerScreen', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DealerScreen],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi('')]
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function answer(email: string, response: AnswerResponse, screen?: DealerScreenFixture): Promise<HTMLElement> {
    const fixture = screen ?? TestBed.createComponent(DealerScreen);
    const request = await paste(fixture, email);
    request.flush(response);
    await fixture.whenStable();
    return fixture.nativeElement;
  }

  type DealerScreenFixture = ReturnType<typeof TestBed.createComponent<DealerScreen>>;

  async function paste(fixture: DealerScreenFixture, email: string) {
    const screen = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    const pasted = screen.querySelector('textarea')!;
    pasted.value = email;
    pasted.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    button(screen, 'Answer it').click();
    const request = http.expectOne('/api/answers');
    expect(request.request.body).toEqual({ email });
    return request;
  }

  function button(screen: HTMLElement, label: string): HTMLButtonElement {
    const found = [...screen.querySelectorAll('button')].find(candidate => candidate.textContent?.trim() === label);
    expect(found, `a button "${label}"`).toBeTruthy();
    return found!;
  }

  it('answers a pasted email with one sentence per check, the Privacy Gate line and the reply', async () => {
    const screen = await answer(ANNAS_EMAIL, ANNAS_ANSWER);

    const checks = [...screen.querySelectorAll('[data-verdict]')];
    expect(checks.map(check => check.getAttribute('data-verdict'))).toEqual(['NOT_ELIGIBLE', 'ELIGIBLE']);
    expect(checks[0].textContent).toContain('LOC90000101');
    expect(checks[0].textContent).toContain('cannot buy');
    expect(checks[1].textContent).toContain('can buy');
    expect(screen.textContent).toContain('LOC90000101 → 3f6c');
    expect(screen.textContent).toContain('the LLM saw Portfolio Ids only');
    expect(screen.querySelector<HTMLTextAreaElement>('textarea[aria-label="Reply"]')?.value)
      .toBe(ANNAS_ANSWER.reply!.text);
  });

  it('shows the Privacy Gate refusal, with what it found, in place of an answer', async () => {
    const screen = await answer('Hi desk, can LOC90009999 subscribe EUR 80,000?', {
      refusal: { reason: 'Portfolio Reference the bank does not know', found: ['LOC90009999'] },
      swaps: [],
      checks: []
    });

    const refusal = screen.querySelector('[role="alert"]');
    expect(refusal?.textContent).toContain('No AI used for this email');
    expect(refusal?.textContent).toContain('LOC90009999');
    expect(screen.querySelector('[data-verdict]')).toBeNull();
    expect(screen.textContent).not.toContain('the LLM saw Portfolio Ids only');
  });

  it('drops the last answer when the next email cannot be answered, and says so', async () => {
    const fixture = TestBed.createComponent(DealerScreen);
    await answer(ANNAS_EMAIL, ANNAS_ANSWER, fixture);

    const request = await paste(fixture, 'Hi desk, can LOC90000102 subscribe?');
    request.flush('down', { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();

    const screen = fixture.nativeElement as HTMLElement;
    expect(screen.querySelector('[data-verdict]')).toBeNull();
    expect(screen.querySelector('textarea[aria-label="Reply"]')).toBeNull();
    expect(screen.querySelector('[role="alert"]')?.textContent).toContain('Could not answer this email');
  });

  it('shows no Privacy Gate line when no Portfolio Id was swapped in', async () => {
    const screen = await answer(ANNAS_EMAIL, { ...ANNAS_ANSWER, swaps: [] });

    expect(screen.textContent).not.toContain('the LLM saw Portfolio Ids only');
  });
});
