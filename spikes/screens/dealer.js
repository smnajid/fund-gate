// PROTOTYPE dealer screen: three structurally different variants over the same scenarios.
// A: single column, top to bottom.  B: three-pane workbench (inbox | request | detail).  C: reply-first answer sheet.

const D = {
  get s() { return App.s.dealer; },
  get sc() { return SCENARIOS[App.s.scenario]; },
  check() { this.s.stage = 'checked'; this.s.selected = this.sc.checks[0] ?? null; App.render(); },
  select(id) { this.s.selected = id; App.render(); },
  record(open) { this.s.recordOpen = open; App.render(); },
  advise(id, i, v) { this.s.advisory[id + ':' + i] = v; App.render(); },
  scenario(k) { App.set({ scenario: k, dealer: freshDealer() }); },
  editReply(on) { this.s.reply.editing = on; App.render(); },
  saveReply(t) { this.s.reply.text = t; },
  manual(k, v) { this.s.manual[k] = v; },
  copy() { this.s.reply.copied = new Date().toLocaleTimeString(); App.render(); },
};

// ---------- shared pieces (content only; each variant owns its layout) ----------

function gateLine(sc) {
  if (!sc.gate) return '';
  if (!sc.gate.ok) return '';
  return `<div class="gate-ok">✓ Privacy Gate: ${sc.gate.swaps.map(([r, p]) => `${r} → <span class="swap">${p}</span>`).join(', ')}. The LLM saw Portfolio Ids only.</div>`;
}

function gateStop(sc) {
  return `<div class="gate-stop">
    <h3>No AI used for this email</h3>
    <div>The Privacy Gate found <b>${esc(sc.gate.reason)}</b> (“${esc(sc.gate.found)}”, ${esc(sc.gate.where)}). Requesters send Portfolio References only, so nothing was sent to the LLM.</div>
    <div class="small muted" style="margin-top:6px">Enter the request by hand below. The Rule Engine needs no AI, so the check runs as usual. A template reply replaces the drafted one.</div>
  </div>`;
}

function manualForm() {
  const m = D.s.manual;
  return `<div class="panel"><h3>Enter the Subscription Request by hand</h3>
    <div class="row">
      <label>Portfolio Reference <input value="${esc(m.ref)}" placeholder="LOC90000101" oninput="D.manual('ref', this.value)"></label>
      <label>Share Class <select onchange="D.manual('cls', this.value)"><option value="">choose…</option>${Object.keys(CLASSES).map((k) => `<option ${m.cls === k ? 'selected' : ''} value="${k}">${clsName(k)}</option>`).join('')}</select></label>
      <label>Amount <input size="10" value="${esc(m.amount)}" oninput="D.manual('amount', this.value)"></label>
      <select onchange="D.manual('ccy', this.value)">${['EUR', 'CHF', 'USD'].map((c) => `<option ${m.ccy === c ? 'selected' : ''}>${c}</option>`).join('')}</select>
      <label><input type="checkbox" ${m.init ? 'checked' : ''} onchange="D.manual('init', this.checked)"> client initiated</label>
      <button class="primary" onclick="alert('PROTOTYPE: would run the Rule Engine with no LLM. For LOC90000101 + GCE-BP + EUR 80,000 the answer is row 3: Eligible.')">Check</button>
    </div></div>`;
}

function requestsTable(sc, compact) {
  return `<table>
    <tr><th>Portfolio</th><th>Share Class</th><th>Amount</th>${compact ? '' : '<th>Client initiated</th>'}</tr>
    ${sc.requests.map((r) => `<tr>
      <td class="mono" style="white-space:nowrap">${r.ref}</td>
      <td><select style="max-width:${compact ? 150 : 320}px">${Object.keys(CLASSES).map((k) => `<option ${k === r.cls ? 'selected' : ''}>${clsName(k)}</option>`).join('')}</select>${r.fanout ? ' <span class="small muted">fan-out</span>' : ''}</td>
      <td style="white-space:nowrap"><input size="${compact ? 6 : 8}" value="${r.amount}"> ${r.ccy}</td>
      ${compact ? '' : `<td><input type="checkbox" ${r.init ? 'checked' : ''}></td>`}
    </tr>`).join('')}
  </table>${sc.fanoutNote ? `<div class="small muted" style="margin-top:4px">${sc.fanoutNote}</div>` : ''}`;
}

function rulesList(c, dense) {
  return `<table>${RULE_ORDER.filter((k) => c.rules[k]).map((k) => {
    const r = c.rules[k];
    return `<tr><td style="width:30px">${oc(r.outcome)}</td><td style="width:${dense ? 130 : 170}px"><b>${RULE_LABELS[k]}</b></td><td>${esc(r.text)}</td><td style="width:150px">${cite(r.cite)}</td></tr>`;
  }).join('')}</table>`;
}

function advisoryBlock(c) {
  if (!c.advisory) return '';
  return `<div class="ai" style="margin-top:10px">
    <div class="row"><span class="ai-tag">✦ AI suggestions · advisory lane</span><span class="spacer"></span><span class="small muted">Can keep or downgrade the Verdict, never upgrade it (ADR 0005)</span></div>
    ${c.advisory.map((a, i) => {
      const st = D.s.advisory[c.row + ':' + i];
      return `<div style="margin-top:8px; ${st === 'dismissed' ? 'opacity:.4; text-decoration:line-through' : ''}">
        <div class="row"><b>${esc(a.what)}</b><span class="spacer"></span>
          ${st ? `<span class="small">${st === 'accepted' ? '✓ added to the dealer\'s to-do' : 'dismissed'}</span>` :
            `<button class="ghost" onclick="D.advise(${c.row},${i},'accepted')">Accept</button><button class="ghost" onclick="D.advise(${c.row},${i},'dismissed')">Dismiss</button>`}
        </div><div class="small">${esc(a.why)}</div></div>`;
    }).join('')}
  </div>`;
}

function replyHtml(sc) {
  const text = D.s.reply.text ?? sc.reply;
  return esc(text).replace(/\[(\d+)\]/g, (m, n) => {
    const ref = sc.replyCites?.[n];
    if (!ref) return m;
    const r = CHECKS[ref[0]].rules[ref[1]];
    return `<sup>${cite(r.cite, '[' + n + ']')}</sup>`;
  });
}

function replyBlock(sc, big) {
  if (!sc.reply) return '';
  const editing = D.s.reply.editing;
  return `<div class="row"><span class="ai-tag">✦ Drafted by AI · edit before sending</span><span class="spacer"></span>
      <button class="ghost" onclick="D.editReply(${!editing})">${editing ? 'Done' : 'Edit'}</button>
      <button class="primary" onclick="D.copy()">Copy reply</button></div>
    ${D.s.reply.copied ? `<div class="small gate-ok">Copied at ${D.s.reply.copied}; recorded in the Check Record.</div>` : ''}
    ${editing
      ? `<textarea rows="12" oninput="D.saveReply(this.value)">${esc(D.s.reply.text ?? sc.reply)}</textarea>`
      : `<div style="white-space:pre-wrap; margin-top:8px; ${big ? 'font-size:15.5px; line-height:1.6' : ''}">${replyHtml(sc)}</div>`}`;
}

function recordContent(sc) {
  const ids = sc.checks || sc.reverse?.checks || [];
  return `<h4>Check Record · append-only</h4>
    <table class="small">
      <tr><th>Check</th><th>Rule Card</th><th>Verdict</th></tr>
      ${ids.map((id) => { const c = CHECKS[id]; return `<tr><td class="mono">${c.ref} × ${CLASSES[c.cls].cls}</td><td>${CLASSES[c.cls].card}</td><td>${verdict(c.verdict)}</td></tr>`; }).join('')}
    </table>
    <div class="small muted" style="margin:6px 0 14px">Engine 0.1.0 · Scale Mapping v1 · Client Profile fields read: classification, mandate type, residence, nationalities, US Person flags, risk profile, losses, horizon, knowledge (kept locally, never sent).</div>
    <h4>What the LLM saw</h4>
    ${sc.llm.length ? sc.llm.map((l) => `<div class="panel" style="margin-bottom:10px">
      <div class="row"><b>${l.call}</b><span class="spacer"></span><span class="small muted">${l.model}</span></div>
      <div class="small" style="margin:4px 0">Client fields sent: ${l.fields.length ? l.fields.map((f) => `<span class="swap">${esc(f)}</span>`).join(' ') : '<i>none</i>'}</div>
      <div class="small muted">Prompt (after the Privacy Gate)</div><pre>${esc(l.sent)}</pre>
      <div class="small muted">Response</div><pre>${esc(l.got)}</pre></div>`).join('')
      : '<div class="muted">No LLM call for this check.</div>'}`;
}

function recordDrawer(sc) {
  if (!D.s.recordOpen) return '';
  return `<aside class="drawer"><div class="row"><h3>Check Record</h3><span class="spacer"></span><button class="ghost" onclick="D.record(false)">Close ✕</button></div>${recordContent(sc)}</aside>`;
}

const checkTitle = (c) => `<span class="mono">${c.ref}</span> → ${clsName(c.cls)} · ${c.amount}`;

// ---------- Variant A: single column ----------
function dealerA() {
  const sc = D.sc, s = D.s;
  let h = '<div class="wrap">';
  if (sc.reverse) h += reverseA(sc);
  else {
    h += `<section class="panel"><h2>1 · Request</h2><textarea rows="7">${esc(sc.email)}</textarea>
      <div class="row" style="margin-top:8px"><button class="primary" onclick="D.check()" ${s.stage === 'checked' ? 'disabled' : ''}>Check</button>
      ${s.stage === 'checked' ? gateLine(sc) : '<span class="small muted">Paste an email or drop an .eml</span>'}<span class="spacer"></span>
      ${s.stage === 'checked' && sc.gate.ok ? '<button class="ghost" onclick="D.record(true)">View Check Record</button>' : ''}</div></section>`;
    if (s.stage === 'checked' && !sc.gate.ok) h += gateStop(sc) + manualForm();
    if (s.stage === 'checked' && sc.gate.ok) {
      h += `<section class="panel"><h2>2 · Subscription Requests <span class="small muted">parsed by AI, edit if wrong</span></h2>${requestsTable(sc)}</section>`;
      h += `<section><h2 style="margin:6px 0 10px">3 · Verdicts</h2><div style="display:grid; gap:10px">${sc.checks.map((id) => {
        const c = CHECKS[id];
        return `<div class="panel bar-${vClass(c.verdict)}">
          <div class="row">${c.suggested ? '<span class="small muted">Alternative found ·</span>' : ''}<h3 style="margin:0">${checkTitle(c)}</h3><span class="spacer"></span>${verdict(c.verdict)}</div>
          <div style="margin:4px 0 8px">${esc(c.headline)}</div>
          <details ${c.verdict !== 'Eligible' ? 'open' : ''}><summary class="small muted">Justification · ${Object.keys(c.rules).length} rules</summary>${rulesList(c)}</details>
          ${advisoryBlock(c)}</div>`;
      }).join('')}</div></section>`;
      h += `<section class="panel"><h2>4 · Reply to the Requester</h2>${replyBlock(sc)}</section>`;
    }
  }
  h += '</div>' + recordDrawer(sc);
  document.getElementById('root').innerHTML = h;
}

function reverseA(sc) {
  const r = sc.reverse;
  return `<section class="panel"><h2>What can this portfolio buy?</h2>
      <div class="row"><input class="mono" value="${r.ref}" size="14"><input value="${r.amount}" size="14"><button class="primary">Run</button><span class="small muted">Rule Engine against every Validated Share Class · no AI</span></div></section>
    <section class="panel"><table>
      <tr><th>Share Class</th><th>ISIN</th><th>Verdict</th><th>Why</th><th>Citation</th></tr>
      ${r.checks.map((id) => { const c = CHECKS[id]; const d = c.rules[c.decisive];
        return `<tr class="clickable ${D.s.selected === id ? 'sel' : ''}" onclick="D.select(${D.s.selected === id ? 'null' : id})">
          <td><b>${clsName(c.cls)}</b></td><td class="mono small">${CLASSES[c.cls].isin}</td><td>${verdict(c.verdict)}</td>
          <td>${oc(d.outcome)} ${RULE_LABELS[c.decisive]}: ${esc(c.headline)}</td><td>${cite(d.cite)}</td></tr>
          ${D.s.selected === id ? `<tr><td colspan="5" style="background:#fafbfc">${rulesList(c, true)}</td></tr>` : ''}`; }).join('')}
    </table></section>`;
}

// ---------- Variant B: three-pane workbench ----------
function dealerB() {
  const sc = D.sc, s = D.s;
  const inbox = Object.entries(SCENARIOS).map(([k, x]) => {
    const subj = x.email ? x.email.match(/(Subject|Objet) ?: (.*)/)[2] : 'Portfolio lookup';
    const from = x.email ? x.email.match(/(From|De) ?: ([^(\n]*)/)[2].trim() : '';
    return `<div onclick="D.scenario('${k}')" style="padding:10px 12px; border-bottom:1px solid var(--line); cursor:pointer; ${k === App.s.scenario ? 'background:var(--accent-soft)' : ''}">
      <div class="small muted">${x.reverse ? '🔎 lookup' : esc(from)}</div><div style="font-weight:${k === App.s.scenario ? 600 : 400}">${esc(subj)}</div></div>`;
  }).join('');

  let mid = '', right = '';
  if (sc.reverse) {
    mid = `<div style="padding:14px"><h3>What can this portfolio buy?</h3>
      <label class="small">Portfolio Reference<br><input class="mono" value="${sc.reverse.ref}"></label><br><br>
      <label class="small">Amount<br><input value="${sc.reverse.amount}"></label><br><br>
      <button class="primary">Run lookup</button><p class="small muted">Deterministic: the Rule Engine against every Validated Share Class. No AI.</p>
      <h4 style="margin-top:16px">Result</h4>
      ${sc.reverse.checks.map((id) => { const c = CHECKS[id]; return `<div class="row" style="padding:4px 0">${verdict(c.verdict)} <span>${clsName(c.cls)}</span></div>`; }).join('')}</div>`;
    right = reverseMatrix(sc);
  } else {
    mid = `<div style="padding:14px; display:grid; grid-template-columns:minmax(0,1fr); gap:12px">
      <div><h4>Request</h4><div class="panel small" style="white-space:pre-wrap; overflow-wrap:anywhere">${esc(sc.email)}</div>
      ${s.stage === 'pasted' ? '<button class="primary" style="margin-top:8px" onclick="D.check()">Check</button>' : `<div style="margin-top:6px">${gateLine(sc)}</div>`}</div>
      ${s.stage === 'checked' && sc.gate.ok ? `<div><h4>Subscription Requests</h4>${requestsTable(sc, true)}</div>
        <div><h4>Checks</h4>${sc.checks.map((id) => { const c = CHECKS[id];
          return `<div onclick="D.select(${id})" class="row bar-${vClass(c.verdict)}" style="padding:8px 10px; margin-bottom:6px; border-radius:6px; cursor:pointer; background:${s.selected === id ? 'var(--accent-soft)' : '#fff'}; border:1px solid var(--line)">
            <div><div class="mono small">${c.ref}</div><div>${CLASSES[c.cls].cls}${c.suggested ? ' <span class="small muted">(alternative)</span>' : ''}</div></div><span class="spacer"></span>${verdict(c.verdict)}</div>`; }).join('')}</div>` : ''}
    </div>`;
    if (s.stage === 'checked' && !sc.gate.ok) right = `<div style="padding:16px; display:grid; gap:12px">${gateStop(sc)}${manualForm()}</div>`;
    else if (s.stage === 'checked') right = detailB(sc);
    else right = '<div style="padding:40px" class="muted">Run the check to see Verdicts.</div>';
  }
  document.getElementById('root').innerHTML = `
    <div style="display:grid; grid-template-columns: 240px 400px minmax(0,1fr); height: calc(100vh - 44px); background:#fff">
      <nav style="border-right:1px solid var(--line); overflow:auto"><div style="padding:10px 12px"><h4>Inbox · fund desk</h4></div>${inbox}</nav>
      <section style="border-right:1px solid var(--line); overflow:auto; background:var(--bg); min-width:0">${mid}</section>
      <section style="overflow:auto">${right}</section>
    </div>`;
}

function detailB(sc) {
  const tab = D.s.tab || 'just';
  const c = CHECKS[D.s.selected];
  const tabs = [['just', 'Justification'], ['reply', 'Reply (all checks)'], ['record', 'Check Record']];
  const tabBar = `<div class="row" style="border-bottom:1px solid var(--line); padding:0 16px">${tabs.map(([k, l]) =>
    `<button class="ghost" style="border-radius:0; padding:10px 8px; ${tab === k ? 'border-bottom:2px solid var(--accent); font-weight:600' : ''}" onclick="App.s.dealer.tab='${k}'; App.render()">${l}</button>`).join('')}</div>`;
  let body = '';
  if (tab === 'just') body = `<div class="row"><h2 style="margin:0">${checkTitle(c)}</h2><span class="spacer"></span>${verdict(c.verdict)}</div>
      <p>${esc(c.headline)}</p>${rulesList(c)}${advisoryBlock(c)}`;
  if (tab === 'reply') body = replyBlock(sc);
  if (tab === 'record') body = recordContent(sc);
  return tabBar + `<div style="padding:16px">${body}</div>`;
}

function reverseMatrix(sc) {
  // Rules down, Share Classes across: the US Person row is the story.
  const ids = sc.reverse.checks;
  return `<div style="padding:16px"><h3>${sc.reverse.ref} · ${sc.reverse.amount}</h3>
    <p class="small muted">Rules down, Share Classes across. Click a cell for its Citation.</p>
    <table><tr><th></th>${ids.map((id) => `<th style="text-align:center">${clsName(CHECKS[id].cls).replace('BNP Paribas (CH) Bonds CHF', 'BNP Bonds CHF').replace('Swiss Life iFunds (CH)', 'Swiss Life').replace('Nordea SIF – Global Private Equity', 'Nordea SIF PE')}</th>`).join('')}</tr>
      ${RULE_ORDER.map((k) => `<tr ${k === 'US' ? 'style="background:#fffbea"' : ''}><td><b>${RULE_LABELS[k]}</b></td>${ids.map((id) => { const r = CHECKS[id].rules[k];
        return `<td style="text-align:center">${r ? (r.cite ? `<span title="${esc(r.text)}">${oc(r.outcome)}</span><br>${cite(r.cite, 'src')}` : `<span title="${esc(r.text)}">${oc(r.outcome)}</span>`) : oc(null)}</td>`; }).join('')}</tr>`).join('')}
      <tr><td><b>Verdict</b></td>${ids.map((id) => `<td style="text-align:center">${verdict(CHECKS[id].verdict)}</td>`).join('')}</tr>
    </table></div>`;
}

// ---------- Variant C: reply-first answer sheet ----------
function dealerC() {
  const sc = D.sc, s = D.s;
  let h = '<div class="wrap" style="max-width:980px">';
  if (sc.reverse) h += reverseC(sc);
  else if (s.stage === 'pasted') {
    h += `<div class="panel" style="padding:28px"><h2>Paste the Requester's email</h2><textarea rows="8">${esc(sc.email)}</textarea>
      <div class="row" style="margin-top:10px"><button class="primary" onclick="D.check()">Answer it</button><span class="small muted">or drop an .eml file</span></div></div>`;
  } else if (!sc.gate.ok) h += gateStop(sc) + manualForm();
  else {
    const from = sc.email.match(/(From|De) ?: ([^(\n]*)/)[2].trim();
    h += `<details class="panel"><summary><b>Request from ${esc(from)}</b> · ${gateLine(sc)}</summary><pre style="white-space:pre-wrap">${esc(sc.email)}</pre>${requestsTable(sc)}</details>`;
    h += `<div style="display:grid; gap:6px">${sc.checks.map((id) => { const c = CHECKS[id];
      const verb = { 'Eligible': 'can buy', 'Not Eligible': 'cannot buy', 'Needs Review': 'needs review for' }[c.verdict];
      return `<div class="panel bar-${vClass(c.verdict)}" style="padding:10px 14px; font-size:16px"><span class="mono">${c.ref}</span> <b>${verb}</b> ${clsName(c.cls)}${c.suggested ? ' <span class="small muted">(alternative)</span>' : ''}. <span class="muted">${esc(c.headline)}</span></div>`; }).join('')}</div>`;
    h += `<div style="display:grid; grid-template-columns: 1fr 300px; gap:14px; align-items:start">
      <div class="panel" style="padding:22px">${replyBlock(sc, true)}</div>
      <aside style="display:grid; gap:8px"><h4>Evidence for the reply</h4>${Object.entries(sc.replyCites).map(([n, [id, k]]) => { const r = CHECKS[id].rules[k];
        return `<div class="panel small"><div class="row"><b>[${n}]</b>${oc(r.outcome)}<span>${RULE_LABELS[k]} · ${CLASSES[CHECKS[id].cls].cls}</span></div>
          <blockquote><span class="quote">${esc(r.cite.quote)}</span></blockquote><div class="muted">${esc(r.cite.doc)}${r.cite.page ? ' p.' + r.cite.page : ''}</div></div>`; }).join('')}
        <button onclick="D.record(true)">What the AI saw (Check Record)</button></aside></div>`;
    h += sc.checks.filter((id) => CHECKS[id].advisory).map((id) => advisoryBlock(CHECKS[id])).join('');
    h += `<details class="panel"><summary><b>Full Justification</b> · every rule of every check</summary>${sc.checks.map((id) => `<h3 style="margin-top:12px">${checkTitle(CHECKS[id])} ${verdict(CHECKS[id].verdict)}</h3>${rulesList(CHECKS[id], true)}`).join('')}</details>`;
  }
  h += '</div>' + recordDrawer(sc);
  document.getElementById('root').innerHTML = h;
}

function reverseC(sc) {
  const r = sc.reverse;
  const groups = [['Eligible', 'Can buy'], ['Needs Review', 'Needs review'], ['Not Eligible', 'Cannot buy']];
  return `<div class="panel" style="padding:22px"><div style="font-size:20px"><span class="mono">${r.ref}</span> with <b>${r.amount}</b>: what can it buy?</div>
      <div class="small muted">Rule Engine against every Validated Share Class · no AI · same answer every time</div></div>
    ${groups.map(([v, l]) => { const ids = r.checks.filter((id) => CHECKS[id].verdict === v); if (!ids.length) return '';
      return `<div><h2 style="margin:8px 0">${l} <span class="muted">(${ids.length})</span></h2><div style="display:grid; gap:6px">${ids.map((id) => { const c = CHECKS[id], d = c.rules[c.decisive];
        return `<div class="panel bar-${vClass(v)}" style="padding:10px 14px"><div style="font-size:15.5px"><b>${clsName(c.cls)}</b></div>
          <div>${esc(c.headline)}</div>${d.cite ? `<blockquote><span class="quote">${esc(d.cite.quote)}</span> <span class="muted small">${esc(d.cite.doc)}${d.cite.page ? ' p.' + d.cite.page : ''}</span></blockquote>` : ''}</div>`; }).join('')}</div></div>`; }).join('')}`;
}

App.screens.dealer = { A: dealerA, B: dealerB, C: dealerC };
