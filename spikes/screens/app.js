// PROTOTYPE shell: in-memory state, URL sync, the floating variant switcher, shared helpers.
// State survives variant switches (history.replaceState, no reload) so you can compare the same moment across variants.


const VARIANTS = {
  dealer: { A: 'Single column, top to bottom', B: 'Three-pane workbench', C: 'Reply-first answer sheet' },
  validation: { A: 'Document beside the rules', B: 'One rule at a time', C: 'Dense table' },
};

const App = {
  s: null,
  screens: {},

  start() {
    const q = new URLSearchParams(location.search);
    const screen = VARIANTS[q.get('screen')] ? q.get('screen') : 'dealer';
    const variant = VARIANTS[screen][q.get('variant')] ? q.get('variant') : 'A';
    const scenario = SCENARIOS[q.get('scenario')] ? q.get('scenario') : 'anna';
    this.s = {
      screen, variant, scenario,
      dealer: freshDealer(),
      validation: freshValidation(),
      showState: false,
    };
    if (q.get('stage') === 'checked') { this.s.dealer.stage = 'checked'; this.s.dealer.selected = SCENARIOS[scenario].checks?.[0] ?? null; }
    document.addEventListener('keydown', (e) => {
      const t = e.target;
      if (t.matches('input, textarea, select, [contenteditable]')) return;
      if (e.key === 'ArrowLeft') this.cycle(-1);
      if (e.key === 'ArrowRight') this.cycle(1);
      if (e.key === 'Escape') { closePop(); if (this.s.dealer.recordOpen) { this.s.dealer.recordOpen = false; this.render(); } }
    });
    document.addEventListener('click', (e) => { if (!e.target.closest('.pop, .cite')) closePop(); });
    this.render();
  },

  set(patch) { Object.assign(this.s, patch); this.render(); },
  cycle(dir) {
    const keys = Object.keys(VARIANTS[this.s.screen]);
    const i = (keys.indexOf(this.s.variant) + dir + keys.length) % keys.length;
    this.set({ variant: keys[i] });
  },

  render() {
    const s = this.s;
    const q = new URLSearchParams({ screen: s.screen, variant: s.variant });
    if (s.screen === 'dealer') q.set('scenario', s.scenario);
    if (s.screen === 'dealer' && s.dealer.stage === 'checked') q.set('stage', 'checked');
    history.replaceState(null, '', '?' + q);
    closePop();
    document.getElementById('app-title').textContent = s.screen === 'dealer' ? 'Eligibility Checks' : 'Rule Card validation';
    document.getElementById('who').textContent = s.screen === 'dealer' ? 'Fund Dealer · P. Meier' : 'Rule Card Validator · J. Keller (Compliance)';
    this.screens[s.screen][s.variant]();
    renderBar();
    const st = document.getElementById('proto-state');
    st.hidden = !s.showState;
    if (s.showState) st.textContent = JSON.stringify({ screen: s.screen, variant: s.variant, scenario: s.scenario, [s.screen]: s[s.screen] }, null, 1);
  },
};

function freshDealer() {
  return { stage: 'pasted', selected: null, recordOpen: false, advisory: {}, reply: {}, manual: { ref: '', cls: '', amount: '', ccy: 'EUR', init: false } };
}
function freshValidation() {
  // Rule state per index: 'pending' | 'accepted' | 'corrected'. Unmodelled default Blocking (ADR 0003).
  const rules = window.DRAFT_CARD_RULES.map((r) => ({
    status: 'pending',
    value: valueOf(r),
    marking: r.kind === 'unmodelled_restriction' ? 'Blocking' : null,
    resolution: r.contradiction ? null : undefined,
  }));
  return { rules, cursor: 0, page: null, validated: null };
}

function renderBar() {
  const s = App.s;
  const v = VARIANTS[s.screen];
  const scen = s.screen === 'dealer'
    ? `<span class="sep"></span><select onchange="App.set({scenario:this.value, dealer: freshDealer()})">${Object.entries(SCENARIOS).map(([k, x]) => `<option value="${k}" ${k === s.scenario ? 'selected' : ''}>${x.label}</option>`).join('')}</select>`
    : '';
  document.getElementById('proto-bar').innerHTML = `
    <button class="${s.screen === 'dealer' ? 'on' : ''}" onclick="App.set({screen:'dealer', variant:'A'})">Dealer</button>
    <button class="${s.screen === 'validation' ? 'on' : ''}" onclick="App.set({screen:'validation', variant:'A'})">Validation</button>
    <span class="sep"></span>
    <button onclick="App.cycle(-1)" title="previous variant (←)">←</button>
    <span class="lbl">${s.variant} · ${v[s.variant]}</span>
    <button onclick="App.cycle(1)" title="next variant (→)">→</button>
    ${scen}
    <span class="sep"></span>
    <button onclick="App.s.screen==='dealer' ? App.set({dealer: freshDealer()}) : App.set({validation: freshValidation()})" title="reset this screen">reset</button>
    <button class="${s.showState ? 'on' : ''}" onclick="App.set({showState: !App.s.showState})">state</button>`;
}

// ---------- shared helpers ----------
const esc = (t) => String(t ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const vClass = (v) => ({ 'Eligible': 'eligible', 'Not Eligible': 'not', 'Needs Review': 'review' }[v]);
const verdict = (v) => `<span class="verdict v-${vClass(v)}">${v}</span>`;
const oc = (o) => o ? `<span class="oc oc-${o}" title="${{ P: 'Pass', F: 'Fail', R: 'Review', W: 'Warning only' }[o]}">${o}</span>` : '<span class="oc oc-none">–</span>';
const clsName = (k) => `${CLASSES[k].fund.replace('Nordea 1 – ', 'Nordea 1 ').replace('Global Climate and Environment', 'Global Climate')} ${CLASSES[k].cls}`;

// Citation chip: click opens a popover with the quoted passage.
let citeSeq = 0; const citeStore = {};
function cite(c, label) {
  if (!c) return '';
  const id = 'c' + (++citeSeq); citeStore[id] = c;
  const where = label ?? (c.page ? `${c.doc} p.${c.page}` : c.doc);
  return `<span class="cite" onclick="openPop(event,'${id}')">${esc(where)}</span>`;
}
function openPop(e, id) {
  e.stopPropagation(); closePop();
  const c = citeStore[id];
  const d = document.createElement('div');
  d.className = 'pop';
  d.innerHTML = `<div class="small muted">${esc(c.doc)}${c.page ? ' · page ' + c.page : ''} · quote verified on page</div><blockquote><span class="quote">${esc(c.quote)}</span></blockquote>`;
  document.body.appendChild(d);
  const r = e.target.getBoundingClientRect();
  d.style.left = Math.min(r.left, innerWidth - 440) + 'px';
  d.style.top = (r.bottom + 6 + 300 > innerHeight ? r.top - d.offsetHeight - 6 : r.bottom + 6) + 'px';
}
function closePop() { document.querySelectorAll('.pop').forEach((p) => p.remove()); }

// Value summary of a Draft rule, as the validator would edit it.
function valueOf(r) {
  switch (r.kind) {
    case 'investor_category': return (r.allowed_categories || []).join(', ');
    case 'us_person_exclusion': return `${r.us_person_definition}; excludes US citizens: ${r.excludes_us_citizens ? 'yes' : 'no'}`;
    case 'minimum_initial_subscription': return `${r.currency} ${Number(r.amount).toLocaleString('en')}`;
    case 'mandate_only_class': return r.mandate_only ? 'yes' : 'no';
    case 'kid_required_for_retail': return r.kid_available ? 'KID available' : 'no KID';
    case 'target_market': return r.value;
    default: return r.value || '';
  }
}
const KIND_LABEL = {
  investor_category: 'Investor category', us_person_exclusion: 'US Person', minimum_initial_subscription: 'Minimum subscription',
  mandate_only_class: 'Mandate-only class', kid_required_for_retail: 'KID for retail', target_market: 'Target Market', unmodelled_restriction: 'Unmodelled Restriction',
};
