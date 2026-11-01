import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable } from 'rxjs';
import { CardView, CitationView, RuleView, ValidationControllerService } from '../generated/api';

/** A cited page split around its quote, so the quote can be highlighted; null when the quote is not on the page. */
export interface Highlighted {
  before: string;
  quote: string;
  after: string;
}

/** Finds the quote on the page as the quote check does: case and line breaks aside. */
export function highlight(page: string, quote: string): Highlighted | null {
  const words = quote.trim().split(/\s+/).filter(word => word.length > 0);
  if (words.length === 0) {
    return null;
  }
  const pattern = new RegExp(words.map(word => word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('\\s+'), 'i');
  const found = pattern.exec(page);
  if (!found) {
    return null;
  }
  return {
    before: page.slice(0, found.index),
    quote: found[0],
    after: page.slice(found.index + found[0].length)
  };
}

/**
 * The Rule Card validation screen (screens.md): the rule list, the cited page with its quote highlighted, and the
 * decision. It shows only the signals the code checks, never the model's confidence (hypotheses.md, #34).
 */
@Component({
  selector: 'app-validation-screen',
  templateUrl: './validation-screen.html',
  styleUrl: './validation-screen.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ValidationScreen {
  private readonly api = inject(ValidationControllerService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly cards = signal<CardView[]>([]);
  protected readonly card = signal<CardView | undefined>(undefined);
  protected readonly selected = signal(0);
  protected readonly citation = signal(0);
  protected readonly page = signal<string | null | undefined>(undefined);
  protected readonly validator = signal('');
  protected readonly edited = signal('');
  protected readonly notedReason = signal('');
  protected readonly error = signal<string | undefined>(undefined);

  protected readonly rule = computed(() => this.card()?.rules[this.selected()]);
  protected readonly cited = computed<CitationView | undefined>(() => this.rule()?.citations[this.citation()]);
  protected readonly highlighted = computed(() => {
    const page = this.page();
    const cited = this.cited();
    return page && cited ? highlight(page, cited.quote) : null;
  });
  protected readonly ready = computed(
    () => this.card()?.rules.filter(rule => rule.status !== 'OPEN' && !rule.unsettledContradiction).length ?? 0
  );
  protected readonly openContradictions = computed(
    () => this.card()?.contradictions.filter(contradiction => !contradiction.settled) ?? []
  );
  protected readonly editable = computed(() => this.card()?.status === 'DRAFT');

  constructor() {
    this.api
      .cards()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: cards => {
          this.cards.set(cards);
          // The demo opens on the first Draft: the card the validator has work on
          const first = cards.find(card => card.status === 'DRAFT') ?? cards[0];
          if (first) {
            this.show(first, 0);
          }
        },
        error: () => this.error.set('Could not load the Rule Cards.')
      });
  }

  protected open(index: number): void {
    const card = this.cards()[index];
    if (card) {
      this.show(card, 0);
    }
  }

  protected select(index: number): void {
    const card = this.card();
    if (card) {
      this.show(card, index);
    }
  }

  protected showCitation(index: number): void {
    this.citation.set(index);
    this.loadPage();
  }

  protected accept(): void {
    this.step(rule => this.api.accept(this.shareClass(), this.version(), rule.index, { validator: this.validator() }));
  }

  protected reject(): void {
    this.step(rule => this.api.reject(this.shareClass(), this.version(), rule.index, { validator: this.validator() }));
  }

  /** Settles a contradiction: this rule's document governs. */
  protected follow(): void {
    this.step(rule => this.api.follow(this.shareClass(), this.version(), rule.index, { validator: this.validator() }));
  }

  protected saveCorrection(): void {
    let condition: { [key: string]: unknown };
    try {
      condition = JSON.parse(this.edited());
    } catch {
      this.error.set('The corrected value is not JSON.');
      return;
    }
    this.correct(condition);
  }

  /** ADR 0003: Blocking gives Review on every check; Noted passes and stays in the Justification. */
  protected mark(status: 'BLOCKING' | 'NOTED'): void {
    const reason = this.notedReason().trim();
    this.correct({
      kind: 'unmodelled_restriction',
      status,
      notedReason: status === 'NOTED' && reason ? reason : null
    });
  }

  protected validate(): void {
    const card = this.card();
    if (!card) {
      return;
    }
    this.call(this.api.validate(this.shareClass(), this.version(), { validator: this.validator() }), validated => {
      this.replace(validated);
      this.show(validated, this.selected());
    });
  }

  protected kindName(kind: string): string {
    const name = kind.replace(/_/g, ' ');
    return name.charAt(0).toUpperCase() + name.slice(1);
  }

  protected value(rule: RuleView): string {
    const { kind, ...fields } = rule.condition;
    return Object.entries(fields)
      .filter(([, value]) => value !== null && !(Array.isArray(value) && value.length === 0))
      .map(([field, value]) => `${field}: ${Array.isArray(value) ? value.join(', ') : value}`)
      .join(' · ');
  }

  protected statusName(rule: RuleView): string {
    if (rule.unsettledContradiction && rule.status !== 'REJECTED') {
      return '!';
    }
    return rule.status.replace(/_/g, ' ').toLowerCase();
  }

  protected documentName(cited: CitationView): string {
    const type = cited.documentType === 'KID' ? 'KID' : cited.documentType?.toLowerCase() ?? cited.document;
    return `${type} p.${cited.page}`;
  }

  /** "The prospectus says institutional only; the KID says all investors." */
  protected readings(rules: number[]): string {
    const card = this.card();
    if (!card) {
      return '';
    }
    return rules
      .map(index => card.rules[index])
      .map(rule => `${rule.citations[0] ? this.documentName(rule.citations[0]) : 'no quote'}: ${rule.summary}`)
      .join('; ');
  }

  private correct(condition: { [key: string]: unknown }): void {
    this.step(rule =>
      this.api.correct(this.shareClass(), this.version(), rule.index, { validator: this.validator(), condition })
    );
  }

  /** A review step on the selected rule, then the next open rule. */
  private step(call: (rule: RuleView) => Observable<CardView>): void {
    const rule = this.rule();
    if (!rule) {
      return;
    }
    this.call(call(rule), reviewed => {
      this.replace(reviewed);
      const next = reviewed.rules.findIndex(other => other.status === 'OPEN' || other.unsettledContradiction);
      this.show(reviewed, next < 0 ? rule.index : next);
    });
  }

  private call(request: Observable<CardView>, next: (card: CardView) => void): void {
    if (!this.validator().trim()) {
      this.error.set('Enter your name first: every step records who took it.');
      return;
    }
    this.error.set(undefined);
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next,
      error: failure => this.error.set(failure?.error?.detail ?? 'That step failed, try again.')
    });
  }

  private show(card: CardView, rule: number): void {
    this.card.set(card);
    this.selected.set(rule);
    this.citation.set(0);
    const shown = card.rules[rule];
    this.edited.set(shown ? JSON.stringify(shown.condition, null, 2) : '');
    this.notedReason.set(shown && typeof shown.condition['notedReason'] === 'string' ? shown.condition['notedReason'] : '');
    this.loadPage();
  }

  private replace(card: CardView): void {
    this.cards.update(cards =>
      cards.map(other => (other.shareClasses[0] === card.shareClasses[0] ? card : other))
    );
  }

  private loadPage(): void {
    const cited = this.cited();
    this.page.set(undefined);
    if (!cited) {
      return;
    }
    this.api
      .page(cited.document, cited.page)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => this.page.set(page.text),
        // The file is not here: the quote is shown alone
        error: () => this.page.set(null)
      });
  }

  private shareClass(): string {
    return this.card()!.shareClasses[0];
  }

  private version(): number {
    return this.card()!.version;
  }
}
