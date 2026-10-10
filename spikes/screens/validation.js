// PROTOTYPE Rule Card validation screen over the real Draft card from the extraction spike (Nordea 1 GCE, BI-EUR + fund-wide rules).
// A: document beside the rules.  B: one rule at a time.  C: dense table.

const RULES = window.DRAFT_CARD_RULES;

const V = {
  get s() { return App.s.validation; },
  go(i) { this.s.cursor = i; this.s.page = null; App.render(); },
  page(j) { this.s.page = j; App.render(); },
  accept(i) { this.s.rules[i].status = this.s.rules[i].value === valueOf(RULES[i]) ? 'accepted' : 'corrected'; this.next(i); },
  edit(i, v) { this.s.rules[i].value = v; },
  mark(i, m) { this.s.rules[i].marking = m; App.render(); },
  resolve(i, side) { this.s.rules[i].resolution = side; App.render(); },
  reopen(i) { this.s.rules[i].status = 'pending'; App.render(); },
  next(i) { const n = this.s.rules.findIndex((r, j) => j > i && r.status === 'pending'); this.s.cursor = n === -1 ? i : n; this.s.page = null; App.render(); },
  validate() { this.s.validated = { by: 'J. Keller (Compliance)', at: new Date().toISOString().slice(0, 16).replace('T', ' ') }; App.render(); },
};

// A rule is ready when reviewed and, if it has a contradiction, a side was chosen.
const blockers = (i) => {
  const r = V.s.rules[i], out = [];
  if (r.status === 'pending') out.push('not reviewed');
  if (RULES[i].contradiction && !r.resolution) out.push('contradiction open');
  return out;
};
const readyCount = () => RULES.filter((_, i) => !blockers(i).length).length;
const statusIcon = (i) => {
  const r = V.s.rules[i];
  if (RULES[i].contradiction && !r.resolution) return '<span class="oc oc-R" title="contradiction">!</span>';
  return { pending: '<span class="oc oc-W">·</span>', accepted: '<span class="oc oc-P">✓</span>', corrected: '<span class="oc oc-P" title="corrected">✎</span>' }[r.status];
};
const scopeLabel = (r) => r.scope === 'fund' ? 'all classes' : r.share_class;
const conf = (c) => `<span class="small ${c < 0.7 ? '' : 'muted'}" style="${c < 0.7 ? 'color:var(--rev); font-weight:600' : ''}">${Math.round(c * 100)}%${c < 0.7 ? ' low' : ''}</span>`;

function cardHeader() {
  const v = V.s.validated;
  return `<div class="panel row" style="${v ? 'border-color:var(--ok); background:var(--ok-soft)' : ''}">
    <div><h2 style="margin:0">Nordea 1 – Global Climate and Environment · BI-EUR</h2>
    <div class="small muted">LU0348927095 · ${v ? `<b style="color:var(--ok)">Validated v1</b> by ${v.by} at ${v.at} · in force for Eligibility Checks` : '<b>Draft v1</b> · extracted 2026-11-01 from the prospectus (9 pages) and the KID · every quote verified on its page'}</div></div>
    <span class="spacer"></span>
    <div style="text-align:right"><div><b>${readyCount()}</b> / ${RULES.length} rules ready</div>
    ${v ? '' : `<button class="primary" ${readyCount() < RULES.length ? 'disabled' : ''} onclick="V.validate()" title="${readyCount() < RULES.length ? 'Review every rule and settle every contradiction first' : ''}">Validate card</button>`}</div></div>`;
}

// The decision controls for one rule: value, contradiction side, Blocking/Noted, accept.
function decision(i, compact) {
  const r = RULES[i], st = V.s.rules[i], locked = !!V.s.validated;
  let h = '';
  if (r.kind === 'unmodelled_restriction') {
    h += `<div class="row">${['Blocking', 'Noted'].map((m) => `<label><input type="radio" name="m${i}" ${st.marking === m ? 'checked' : ''} ${locked ? 'disabled' : ''} onchange="V.mark(${i},'${m}')"> ${m}</label>`).join('')}
      ${compact ? '' : `<span class="small muted">${st.marking === 'Blocking' ? 'Needs Review on every check' : 'Shown in the Justification, does not affect the Verdict'}</span>`}</div>`;
    if (r.value === 'jurisdiction_registration' && !compact) h += `<div class="small" style="color:var(--rev)">“Legal only where registered” belongs under Fund Registration, not here (ADR 0003): mark it Noted.</div>`;
  } else {
    h += `<label class="small muted">Value <input ${locked ? 'disabled' : ''} value="${esc(st.value)}" size="${compact ? 22 : 36}" onchange="V.edit(${i}, this.value)"></label>`;
  }
  if (r.contradiction) {
    const docs = [...new Set(r.citations.map((c) => c.doc))];
    h += `<div style="margin-top:6px; padding:6px 8px; background:var(--rev-soft); border-radius:6px"><b style="color:var(--rev)">Contradiction</b> ${compact ? '' : '<span class="small">The extractor kept both readings. Pick the one that governs.</span>'}
      <div class="row">${docs.map((d) => `<label><input type="radio" name="r${i}" ${st.resolution === d ? 'checked' : ''} ${locked ? 'disabled' : ''} onchange="V.resolve(${i},'${d}')"> follow ${d}</label>`).join('')}</div></div>`;
  }
  if (!locked) h += `<div class="row" style="margin-top:${compact ? 0 : 8}px">${st.status === 'pending'
    ? `<button class="primary" onclick="V.accept(${i})">${st.value !== valueOf(r) ? 'Save correction' : 'Accept'}</button>`
    : `<span class="small gate-ok">${st.status === 'corrected' ? 'Corrected' : 'Accepted'} by J. Keller</span><button class="ghost" onclick="V.reopen(${i})">Reopen</button>`}</div>`;
  return compact ? `<div class="row" style="align-items:flex-start">${h}</div>` : h;
}

function quotes(r, onPick) {
  return r.citations.map((c, j) => `<blockquote ${onPick ? `style="cursor:pointer" onclick="V.page(${j})"` : ''}><span class="quote">${esc(c.quote)}</span><div class="small muted">${c.doc} p.${c.page} ${c.verified ? '· ✓ found on page' : '· ✗ NOT found on page'}</div></blockquote>`).join('');
}

// ---------- Variant A: document beside the rules ----------
function validationA() {
  const i = V.s.cursor, r = RULES[i], cj = V.s.page ?? 0, c = r.citations[cj];
  const list = RULES.map((x, j) => `<tr class="clickable ${j === i ? 'sel' : ''}" onclick="V.go(${j})"><td style="width:28px">${statusIcon(j)}</td>
      <td><b>${KIND_LABEL[x.kind]}</b>${x.dimension ? ' · ' + x.dimension : ''}<div class="small muted">${esc(V.s.rules[j].value)}</div></td><td class="small muted">${scopeLabel(x)}</td></tr>`).join('');
  const bars = (n, seed) => Array.from({ length: n }, (_, k) => `<div style="height:7px; margin:7px 0; background:#e9ebef; border-radius:3px; width:${60 + ((k * 37 + seed * 13) % 38)}%"></div>`).join('');
  const page = `<div style="background:#fff; border:1px solid var(--line); box-shadow:0 2px 8px rgba(0,0,0,.06); padding:26px 30px; min-height:440px">
      <div class="small muted row"><span>${c.doc === 'prospectus' ? 'Nordea 1, SICAV · Prospectus' : 'Key Information Document · BI-EUR'}</span><span class="spacer"></span><span>page ${c.page}</span></div>
      ${bars(6, c.page)}<p style="font-family:Georgia, serif; font-size:14.5px"><span class="quote">${esc(c.quote)}</span></p>${bars(9, c.page + 1)}</div>`;
  document.getElementById('root').innerHTML = `<div class="wrap" style="max-width:1400px">${cardHeader()}
    <div style="display:grid; grid-template-columns: 320px 1fr 1fr; gap:14px; align-items:start">
      <div class="panel" style="padding:6px 0"><table>${list}</table></div>
      <div><div class="row" style="margin-bottom:6px">${r.citations.map((x, j) => `<button class="${j === cj ? 'primary' : ''}" onclick="V.page(${j})">${x.doc} p.${x.page}</button>`).join('')}</div>${page}</div>
      <div class="panel"><h4>${KIND_LABEL[r.kind]} · ${scopeLabel(r)}</h4><h3>${esc(V.s.rules[i].value)}</h3>
        <div class="row small" style="margin-bottom:6px">Extractor confidence ${conf(r.confidence)}</div>
        <p>${esc(r.summary)}</p>${decision(i)}</div>
    </div></div>`;
}

// ---------- Variant B: one rule at a time ----------
function validationB() {
  const i = V.s.cursor, r = RULES[i];
  const dots = RULES.map((_, j) => `<span onclick="V.go(${j})" style="cursor:pointer; flex:1; height:8px; border-radius:4px; background:${
    j === i ? 'var(--accent)' : blockers(j).length ? (RULES[j].contradiction && !V.s.rules[j].resolution ? 'var(--rev)' : 'var(--line)') : 'var(--ok)'}"></span>`).join('');
  const allDone = readyCount() === RULES.length;
  document.getElementById('root').innerHTML = `<div class="wrap" style="max-width:820px">${cardHeader()}
    <div class="row" style="gap:4px">${dots}</div>
    <div class="panel" style="padding:24px">
      <div class="row"><span class="small muted">Rule ${i + 1} of ${RULES.length} · ${scopeLabel(r)}</span><span class="spacer"></span>${conf(r.confidence)}</div>
      <h2 style="font-size:22px; margin:6px 0">${KIND_LABEL[r.kind]}${r.dimension ? ' · ' + r.dimension : ''}</h2>
      <div style="font-size:18px; margin-bottom:10px">${esc(V.s.rules[i].value)}</div>
      <p class="muted">${esc(r.summary)}</p>
      <h4 style="margin-top:14px">What the documents say</h4>${quotes(r)}
      <div style="margin-top:14px">${decision(i)}</div>
    </div>
    <div class="row"><button onclick="V.go(${Math.max(0, i - 1)})" ${i === 0 ? 'disabled' : ''}>Previous rule</button><span class="spacer"></span>
      ${allDone && !V.s.validated ? '<span class="gate-ok">All rules ready: validate the card above.</span>' : ''}
      <button onclick="V.go(${Math.min(RULES.length - 1, i + 1)})" ${i === RULES.length - 1 ? 'disabled' : ''}>Next rule</button></div></div>`;
}

// ---------- Variant C: dense table ----------
function validationC() {
  const open = RULES.map((r, i) => i).filter((i) => RULES[i].contradiction && !V.s.rules[i].resolution);
  const rows = RULES.map((r, i) => `<tr ${RULES[i].contradiction && !V.s.rules[i].resolution ? 'style="background:#fffaf0"' : ''}>
      <td>${statusIcon(i)}</td><td><b>${KIND_LABEL[r.kind]}</b>${r.dimension ? '<div class="small muted">' + r.dimension + '</div>' : ''}</td><td class="small">${scopeLabel(r)}</td>
      <td style="min-width:320px">${decision(i, true)}</td><td>${conf(r.confidence)}</td>
      <td class="small">${r.citations.map((c) => cite({ doc: c.doc, page: c.page, quote: c.quote })).join('<br>')}</td>
      <td class="small muted" style="max-width:280px">${esc(r.summary.length > 160 ? r.summary.slice(0, 160) + '…' : r.summary)}</td></tr>`).join('');
  document.getElementById('root').innerHTML = `<div class="wrap" style="max-width:1500px">${cardHeader()}
    ${open.length ? `<div class="panel" style="border-color:var(--rev); background:var(--rev-soft)"><b>${open.length} contradictions need a decision</b>: ${open.map((i) => KIND_LABEL[RULES[i].kind] + (RULES[i].dimension ? ' · ' + RULES[i].dimension : '')).join(', ')}. The prospectus says institutional only; the KID says all investors.</div>` : ''}
    <div class="panel" style="overflow-x:auto"><table>
      <tr><th></th><th>Rule</th><th>Scope</th><th>Decision</th><th>Conf.</th><th>Citations</th><th>Extractor note</th></tr>${rows}</table></div></div>`;
}

App.screens.validation = { A: validationA, B: validationB, C: validationC };
