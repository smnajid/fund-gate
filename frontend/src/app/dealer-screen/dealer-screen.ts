import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AnswerControllerService,
  AnswerResponse,
  Check,
  CheckRecordControllerService,
  CheckRecordResponse,
  Outcome
} from '../generated/api';

/** The answer sheet (screens.md, Dealer screen): paste a Requester's email, get one sentence per check and the reply. */
@Component({
  selector: 'app-dealer-screen',
  templateUrl: './dealer-screen.html',
  styleUrl: './dealer-screen.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DealerScreen {
  private readonly answers = inject(AnswerControllerService);
  private readonly checkRecords = inject(CheckRecordControllerService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly email = signal('');
  protected readonly answer = signal<AnswerResponse | undefined>(undefined);
  protected readonly answering = signal(false);
  protected readonly failed = signal(false);
  /** "What the AI saw": the side drawer is open while a check is set; its record arrives after. */
  protected readonly openedCheck = signal<Check | undefined>(undefined);
  protected readonly checkRecord = signal<CheckRecordResponse | undefined>(undefined);
  protected readonly checkRecordFailed = signal(false);

  protected answerIt(): void {
    // An earlier email's Verdicts must never stay on screen for this one
    this.answer.set(undefined);
    this.closeCheckRecord();
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

  protected openCheckRecord(check: Check): void {
    // Another check's record must never stay in the drawer while this one loads
    this.openedCheck.set(check);
    this.checkRecord.set(undefined);
    this.checkRecordFailed.set(false);
    this.checkRecords
      .checkRecord(check.checkId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: record => {
          if (this.openedCheck() === check) {
            this.checkRecord.set(record);
          }
        },
        error: () => {
          if (this.openedCheck() === check) {
            this.checkRecordFailed.set(true);
          }
        }
      });
  }

  protected closeCheckRecord(): void {
    this.openedCheck.set(undefined);
    this.checkRecord.set(undefined);
    this.checkRecordFailed.set(false);
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

  protected ruleName(rule: Outcome.RuleEnum): string {
    switch (rule) {
      case 'INVESTOR_CATEGORY':
        return 'Investor category';
      case 'US_PERSON_EXCLUSION':
        return 'US Person exclusion';
      case 'MINIMUM_INITIAL_SUBSCRIPTION':
        return 'Minimum initial subscription';
      case 'MANDATE_ONLY_CLASS':
        return 'Mandate-only class';
      case 'KID_REQUIRED_FOR_RETAIL':
        return 'KID required for retail';
      case 'UNMODELLED_RESTRICTION':
        return 'Unmodelled Restrictions';
      case 'TARGET_MARKET':
        return 'Target Market';
      case 'FUND_REGISTRATION':
        return 'Fund Registration';
    }
  }

  protected outcomeName(outcome: Outcome.OutcomeEnum): string {
    return outcome.charAt(0) + outcome.slice(1).toLowerCase();
  }

  /** The first characters of a Portfolio Id: enough to show the swap, as on the Privacy Gate line. */
  protected shortId(portfolioId: string): string {
    return portfolioId.slice(0, 4) + '…';
  }
}
