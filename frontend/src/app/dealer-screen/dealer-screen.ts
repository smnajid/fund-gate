import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AnswerControllerService, AnswerResponse, Check } from '../generated/api';

/** The answer sheet (screens.md, Dealer screen): paste a Requester's email, get one sentence per check and the reply. */
@Component({
  selector: 'app-dealer-screen',
  templateUrl: './dealer-screen.html',
  styleUrl: './dealer-screen.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DealerScreen {
  private readonly answers = inject(AnswerControllerService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly email = signal('');
  protected readonly answer = signal<AnswerResponse | undefined>(undefined);
  protected readonly answering = signal(false);
  protected readonly failed = signal(false);

  protected answerIt(): void {
    // An earlier email's Verdicts must never stay on screen for this one
    this.answer.set(undefined);
    this.failed.set(false);
    this.answering.set(true);
    this.answers
      .answer({ email: this.email() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: answer => this.answer.set(answer),
        complete: () => this.answering.set(false),
        error: () => {
          this.failed.set(true);
          this.answering.set(false);
        }
      });
  }

  protected verdictPhrase(verdict: Check.VerdictEnum): string {
    switch (verdict) {
      case 'ELIGIBLE':
        return 'can buy';
      case 'NOT_ELIGIBLE':
        return 'cannot buy';
      case 'NEEDS_REVIEW':
        return 'needs review to buy';
    }
  }

  /** The first characters of a Portfolio Id: enough to show the swap, as on the Privacy Gate line. */
  protected shortId(portfolioId: string): string {
    return portfolioId.slice(0, 4) + '…';
  }
}
