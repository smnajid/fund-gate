// PROTOTYPE dealer screen: three structurally different variants over the same scenarios.
// A: single column, top to bottom.  B: three-pane workbench (inbox | request | detail).  C: reply-first answer sheet.

const D = {
  get s() { return App.s.dealer; },
  get sc() { return SCENARIOS[App.s.scenario]; },
  check() { this.s.stage = 'checked'; this.s.selected = this.sc.checks[0] ?? null; App.render(); },
  select(id) { this.s.selected = id; App.render(); },
  record(open) { this.s.recordOpen = open; App.render(); },
  // Advisory Suggestions (ADR 0007): every decision is appended to the log, which the Check Record shows.
  decide(key, v, note) {
    const at = new Date().toLocaleTimeString();
    if (v) this.s.advisory[key] = { v, note, at }; else delete this.s.advisory[key];
    this.s.log.push({ key, v: v || 'reopened', note, at, by: 'P. Meier' });
    this.s.dismissing = null; App.render();
  },
  startDismiss(key) { this.s.dismissing = key; App.render(); },
  draft(force) {
    if (this.s.reply.edited && !force) { this.s.reply.confirming = true; App.render(); return; }
    this.s.reply.confirming = false;
    Object.assign(this.s.reply, { text: composeReply(this.sc), basis: basisKey(this.sc), drafted: true, edited: false, editing: false }); App.render();
  },
  scenario(k) { App.set({ scenario: k, dealer: freshDealer() }); },
  editReply(on) { this.s.reply.editing = on; App.render(); },
  saveReply(t) { this.s.reply.text = t; this.s.reply.edited = true; },
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
      <button class="primary" onclick="D.s.manualRan = true; App.render()">Check</button>
    </div>${D.s.manualRan ? '<div class="small muted" style="margin-top:8px">Prototype: the real screen runs the Rule Engine here, with no AI. For LOC90000101, BP-EUR and EUR 80,000 the answer is coverage row 3: Eligible.</div>' : ''}</div>`;
}

function requestsTable(sc, compact) {
  return `<div style="overflow-x:auto"><table>
    <tr><th>Portfolio</th><th>Share Class</th><th>Amount</th>${compact ? '' : '<th>Client initiated</th>'}</tr>
    ${sc.requests.map((r) => `<tr>
      <td class="mono" style="white-space:nowrap">${r.ref}</td>
      <td><select style="max-width:${compact ? 150 : 320}px">${Object.keys(CLASSES).map((k) => `<option ${k === r.cls ? 'selected' : ''}>${clsName(k)}</option>`).join('')}</select>${r.fanout ? ' <span class="small muted">fan-out</span>' : ''}</td>
      <td style="white-space:nowrap"><input size="${compact ? 6 : 8}" value="${r.amount}"> ${r.ccy}</td>
      ${compact ? '' : `<td><input type="checkbox" ${r.init ? 'checked' : ''}></td>`}
    </tr>`).join('')}
  </table></div>${sc.fanoutNote ? `<div class="small muted" style="margin-top:4px">${sc.fanoutNote}</div>` : ''}`;
}

function rulesList(c, dense) {
  return `<div style="overflow-x:auto"><table>${RULE_ORDER.filter((k) => c.rules[k]).map((k) => {
    const r = c.rules[k];
    return `<tr><td style="width:30px">${oc(r.outcome)}</td><td style="width:${dense ? 130 : 170}px"><b>${RULE_LABELS[k]}</b></td><td>${esc(r.text)}</td><td style="width:150px">${cite(r.cite)}</td></tr>`;
  }).join('')}</table></div>`;
}

function suggestionsOf(sc) {
  return (sc.checks || []).flatMap((id) => (CHECKS[id].advisory || []).map((a, i) => ({ key: id + ':' + i, c: CHECKS[id], a })));
}
const needsDraft = (sc) => suggestionsOf(sc).length > 0;
const acceptedQuestions = (sc) => suggestionsOf(sc).filter((x) => x.a.to === 'requester' && D.s.advisory[x.key]?.v === 'accepted');
const undecided = (sc) => suggestionsOf(sc).filter((x) => !D.s.advisory[x.key]);
const basisKey = (sc) => acceptedQuestions(sc).map((x) => x.key).join(',');
function composeReply(sc) {
  const qs = acceptedQuestions(sc);
  return sc.reply.replace('{{questions}}', qs.length ? `${sc.questionsIntro}\n   – ${qs.map((x) => x.a.ask).join('\n   – ')}\n` : '');
}
function replyText(sc) {
  if (D.s.reply.text != null) return D.s.reply.text;
  return needsDraft(sc) ? null : sc.reply.replace('{{questions}}', '');
}

function suggestionRow(x) {
  const st = D.s.advisory[x.key], k = x.key;
  let right;
  if (D.s.dismissing === k) right = `<input id="dn-${k}" placeholder="Why? (optional)" size="26"><button class="ghost" onclick="D.decide('${k}','dismissed', document.getElementById('dn-${k}').value)">Dismiss</button><button class="ghost" onclick="D.startDismiss(null)">Cancel</button>`;
  else if (st) right = `<span class="small">${st.v === 'accepted' ? (x.a.to === 'requester' ? '✓ will be asked in the reply' : '✓ recorded as your to-do') : 'dismissed'}</span><button class="ghost" onclick="D.decide('${k}', null)">Change</button>`;
  else right = `<button class="ghost" onclick="D.decide('${k}','accepted')">Accept</button><button class="ghost" onclick="D.startDismiss('${k}')">Dismiss</button>`;
  return `<div style="margin-top:8px">
    <div class="row"><b style="${st?.v === 'dismissed' ? 'opacity:.45; text-decoration:line-through' : ''}">${esc(x.a.what)}</b><span class="spacer"></span>${right}</div>
    <div class="small" style="${st?.v === 'dismissed' ? 'opacity:.45' : ''}">${esc(x.a.why)}</div>
    ${st?.v === 'dismissed' && st.note ? `<div class="small muted">Note: ${esc(st.note)}</div>` : ''}</div>`;
}

function advisoryBlock(c) {
  if (!c.advisory) return '';
  const items = c.advisory.map((a, i) => ({ key: c.row + ':' + i, c, a }));
  const group = (to, label, hint) => {
    const g = items.filter((x) => x.a.to === to);
    return g.length ? `<div style="margin-top:10px"><h4 style="margin:0">${label} <span class="small muted" style="text-transform:none; letter-spacing:0">· ${hint}</span></h4>${g.map(suggestionRow).join('')}</div>` : '';
  };
  return `<div class="ai" style="margin-top:10px">
    <div class="row"><span class="ai-tag">✦ AI suggestions · advisory lane</span><span class="spacer"></span><span class="small muted">Never change the Verdict (ADR 0007)</span></div>
    ${group('requester', 'Ask the Requester', 'accepted ones are asked in the reply')}
    ${group('dealer', 'Dealer to-do', 'internal, never in the reply')}
  </div>`;
}

function replyHtml(sc) {
  return esc(replyText(sc)).replace(/\[(\d+)\]/g, (m, n) => {
    const ref = sc.replyCites?.[n];
    if (!ref) return m;
    const r = CHECKS[ref[0]].rules[ref[1]];
    return `<sup>${cite(r.cite, '[' + n + ']')}</sup>`;
  });
}

function replyBlock(sc, big) {
  if (!sc.reply) return '';
  const r = D.s.reply, und = undecided(sc).length;
  const warnUnd = und ? `<div class="small" style="color:var(--rev)">${und} suggestion${und > 1 ? 's' : ''} undecided: left out of the reply.</div>` : '';
  if (needsDraft(sc) && !r.drafted) {
    return `<div class="row"><span class="ai-tag">✦ Reply</span></div>
      <p class="muted">Decide on the suggestions first: accepted <b>Ask the Requester</b> items become questions in the reply, in the request's language.</p>
      ${warnUnd}<button class="primary" style="margin-top:6px" onclick="D.draft()">Draft reply</button>`;
  }
  const stale = needsDraft(sc) && basisKey(sc) !== r.basis;
  const editing = r.editing;
  return `<div class="row"><span class="ai-tag">✦ Drafted by AI · edit before sending</span><span class="spacer"></span>
      <button class="ghost" onclick="D.editReply(${!editing})">${editing ? 'Done' : 'Edit'}</button>
      <button class="primary" onclick="D.copy()">Copy reply</button></div>
    ${stale ? `<div class="row small" style="background:var(--rev-soft); padding:6px 8px; border-radius:6px; margin-top:6px">Your decisions changed since this draft.<span class="spacer"></span>${r.confirming ? '<span>Redraft replaces your edits.</span><button class="primary" onclick="D.draft(true)">Replace my edits</button><button class="ghost" onclick="App.s.dealer.reply.confirming=false; App.render()">Keep editing</button>' : `<button onclick="D.draft()">Redraft</button>${r.edited ? '<span class="muted">replaces your edits</span>' : ''}`}</div>` : ''}
    ${warnUnd}
    ${r.copied ? `<div class="small gate-ok">Copied at ${r.copied}; recorded in the Check Record.</div>` : ''}
    ${editing
      ? `<textarea rows="14" oninput="D.saveReply(this.value)">${esc(replyText(sc))}</textarea>`
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
    ${needsDraft(sc) ? `<h4>Dealer decisions on Advisory Suggestions</h4>${D.s.log.length ? `<table class="small"><tr><th>When</th><th>Who</th><th>Suggestion</th><th>Decision</th></tr>${D.s.log.map((e) => { const x = suggestionsOf(sc).find((y) => y.key === e.key);
      return `<tr><td>${e.at}</td><td>${e.by}</td><td>${esc(x.a.what)}</td><td>${e.v}${e.note ? ': ' + esc(e.note) : ''}</td></tr>`; }).join('')}</table>` : '<div class="small muted">None yet.</div>'}<div style="height:14px"></div>` : ''}
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
      h += `<section><h2 style="margin:6px 0 10px">3 · Verdicts</h2><div style="display:grid; grid-template-columns:minmax(0,1fr); gap:10px">${sc.checks.map((id) => {
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
    <section class="panel" style="overflow-x:auto"><table>
      <tr><th>Share Class</th><th>ISIN</th><th>Verdict</th><th>Why</th><th>Citation</th></tr>
      ${r.checks.map((id) => { const c = CHECKS[id]; const d = c.rules[c.decisive];
        return `<tr class="clickable ${D.s.selected === id ? 'sel' : ''}" onclick="D.select(${D.s.selected === id ? 'null' : id})">
          <td><b>${clsName(c.cls)}</b></td><td class="mono small">${CLASSES[c.cls].isin}</td><td>${verdict(c.verdict)}</td>
          <td>${oc(d.outcome)} ${RULE_LABELS[c.decisive]}: ${esc(c.headline)}</td><td>${cite(d.cite)}</td></tr>
          ${D.s.selected === id ? `<tr><td colspan="5" style="background:var(--bg)">${rulesList(c, true)}</td></tr>` : ''}`; }).join('')}
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
          return `<div onclick="D.select(${id})" class="row bar-${vClass(c.verdict)}" style="padding:8px 10px; margin-bottom:6px; border-radius:6px; cursor:pointer; background:${s.selected === id ? 'var(--accent-soft)' : 'var(--panel)'}; border:1px solid var(--line)">
            <div><div class="mono small">${c.ref}</div><div>${CLASSES[c.cls].cls}${c.suggested ? ' <span class="small muted">(alternative)</span>' : ''}</div></div><span class="spacer"></span>${verdict(c.verdict)}</div>`; }).join('')}</div>` : ''}
    </div>`;
    if (s.stage === 'checked' && !sc.gate.ok) right = `<div style="padding:16px; display:grid; gap:12px">${gateStop(sc)}${manualForm()}</div>`;
    else if (s.stage === 'checked') right = detailB(sc);
    else right = '<div style="padding:40px" class="muted">Run the check to see Verdicts.</div>';
  }
  document.getElementById('root').innerHTML = `
    <div class="stack-md" style="display:grid; grid-template-columns: 240px 400px minmax(0,1fr); height: calc(100vh - 44px); background:var(--panel)">
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
  return `<div style="padding:16px; overflow-x:auto"><h3>${sc.reverse.ref} · ${sc.reverse.amount}</h3>
    <p class="small muted">Rules down, Share Classes across. Click a cell for its Citation.</p>
    <table><tr><th></th>${ids.map((id) => `<th style="text-align:center">${clsName(CHECKS[id].cls).replace('BNP Paribas (CH) Bonds CHF', 'BNP Bonds CHF').replace('Swiss Life iFunds (CH)', 'Swiss Life').replace('Nordea SIF – Global Private Equity', 'Nordea SIF PE')}</th>`).join('')}</tr>
      ${RULE_ORDER.map((k) => `<tr ${k === 'US' ? 'style="background:var(--mark-soft)"' : ''}><td><b>${RULE_LABELS[k]}</b></td>${ids.map((id) => { const r = CHECKS[id].rules[k];
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
    h += `<div style="display:grid; grid-template-columns:minmax(0,1fr); gap:6px">${sc.checks.map((id) => { const c = CHECKS[id];
      const verb = { 'Eligible': 'can buy', 'Not Eligible': 'cannot buy', 'Needs Review': 'needs review for' }[c.verdict];
      return `<div class="panel bar-${vClass(c.verdict)}" style="padding:10px 14px; font-size:16px"><span class="mono">${c.ref}</span> <b>${verb}</b> ${clsName(c.cls)}${c.suggested ? ' <span class="small muted">(alternative)</span>' : ''}. <span class="muted">${esc(c.headline)}</span></div>`; }).join('')}</div>`;
    h += sc.checks.filter((id) => CHECKS[id].advisory).map((id) => advisoryBlock(CHECKS[id])).join('');
    h += `<div class="stack-md" style="display:grid; grid-template-columns: minmax(0,1fr) 300px; gap:14px; align-items:start">
      <div class="panel" style="padding:22px">${replyBlock(sc, true)}</div>
      <aside style="display:grid; gap:8px"><h4>Evidence for the reply</h4>${Object.entries(sc.replyCites).map(([n, [id, k]]) => { const r = CHECKS[id].rules[k];
        return `<div class="panel small"><div class="row"><b>[${n}]</b>${oc(r.outcome)}<span>${RULE_LABELS[k]} · ${CLASSES[CHECKS[id].cls].cls}</span></div>
          <blockquote><span class="quote">${esc(r.cite.quote)}</span></blockquote><div class="muted">${esc(r.cite.doc)}${r.cite.page ? ' p.' + r.cite.page : ''}</div></div>`; }).join('')}
        <button onclick="D.record(true)">What the AI saw (Check Record)</button></aside></div>`;
    h += `<details class="panel"><summary><b>Full Justification</b> · every rule of every check</summary>${sc.checks.map((id) => `<h3 style="margin-top:12px">${checkTitle(CHECKS[id])} ${verdict(CHECKS[id].verdict)}</h3>${rulesList(CHECKS[id], true)}`).join('')}</details>`;
  }
  h += '</div>' + recordDrawer(sc);
  document.getElementById('root').innerHTML = h;
}

// Reverse lookup, long-list shape: Share Classes as rows, the fixed rule kinds as columns,
// grouped by Verdict; "Cannot buy" collapsed and sub-grouped by the deciding rule.
function lk() { return D.s.lk ??= { q: '', regime: '', long: true, open: { Eligible: true, 'Needs Review': true } }; }
const LK = {
  set(k, v) { lk()[k] = v; lk().focus = k === 'q'; App.render(); },
  toggle(g) { lk().open[g] = !lk().open[g]; App.render(); },
};

function lookupRows(sc) {
  const L = lk(), q = L.q.trim().toLowerCase();
  return [...sc.reverse.checks, ...(L.long ? window.PROTO.SYNTH_IDS : [])].map((id) => CHECKS[id]).filter((c) => {
    const k = CLASSES[c.cls];
    if (L.regime && !k.regime.startsWith(L.regime)) return false;
    return !q || `${k.fund} ${k.cls} ${k.isin}`.toLowerCase().includes(q);
  });
}

function lookupTable(rows) { return `<div style="overflow-x:auto">${lookupTableInner(rows)}</div>`; }
function lookupTableInner(rows) {
  const head = `<tr><th style="position:sticky; top:0; background:var(--panel)">Share Class</th>${RULE_ORDER.map((k) => `<th style="position:sticky; top:0; background:var(--panel); text-align:center; font-size:11px">${RULE_LABELS[k].replace(' subscription', '').replace(' restrictions', '').replace('Fund ', '')}</th>`).join('')}</tr>`;
  return `<table>${head}${rows.map((c) => {
    const k = CLASSES[c.cls];
    const cells = RULE_ORDER.map((r) => {
      const x = c.rules[r];
      const hot = r === c.decisive && x && 'FR'.includes(x.outcome) ? `outline:2px solid ${x.outcome === 'F' ? 'var(--no)' : 'var(--rev)'}; outline-offset:-3px; border-radius:6px;` : '';
      const body = x ? (x.cite ? `<span title="${esc(x.text)}">${oc(x.outcome)}</span><br>${cite(x.cite, 'src')}` : `<span title="${esc(x.text)}">${oc(x.outcome)}</span>`) : oc(null);
      return `<td style="text-align:center; ${hot}">${body}</td>`;
    }).join('');
    return `<tr><td><b>${esc(k.fund)}</b> ${esc(k.cls)}<div class="small muted"><span class="mono">${k.isin}</span> · ${k.regime} · ${esc(c.headline)}</div></td>${cells}</tr>`;
  }).join('')}</table>`;
}

function reverseC(sc) {
  const r = sc.reverse, L = lk(), rows = lookupRows(sc);
  const by = (v) => rows.filter((c) => c.verdict === v);
  const groupHead = (key, label, n, v) => `<div class="row bar-${vClass(v)}" style="padding:10px 14px; background:var(--panel); border:1px solid var(--line); border-radius:8px; cursor:pointer" onclick="LK.toggle('${key}')">
      <span>${L.open[key] ? '▾' : '▸'}</span><h3 style="margin:0">${label}</h3><span class="muted">${n}</span></div>`;
  let h = `<div class="panel" style="padding:20px"><div style="font-size:20px"><span class="mono">${r.ref}</span> with <b>${r.amount}</b>: what can it buy?</div>
      <div class="small muted">Rule Engine against every Validated Share Class · no AI · same answer every time</div>
      <div style="margin-top:8px; font-size:15px">${verdict('Eligible')} <b>${by('Eligible').length}</b> &nbsp; ${verdict('Needs Review')} <b>${by('Needs Review').length}</b> &nbsp; ${verdict('Not Eligible')} <b>${by('Not Eligible').length}</b></div>
      <div class="row" style="margin-top:12px">
        <input id="lk-q" placeholder="Search fund, class or ISIN" size="30" value="${esc(L.q)}" oninput="LK.set('q', this.value)">
        <select onchange="LK.set('regime', this.value)">${[['', 'All regimes'], ['CH', 'CH'], ['LU', 'LU']].map(([v, l]) => `<option value="${v}" ${L.regime === v ? 'selected' : ''}>${l}</option>`).join('')}</select>
        <span class="spacer"></span>
        <label class="small"><input type="checkbox" ${L.long ? 'checked' : ''} onchange="LK.set('long', this.checked)"> prototype: pad with ${window.PROTO.SYNTH_IDS.length} synthetic Share Classes</label>
      </div></div>`;
  for (const [v, label] of [['Eligible', 'Can buy'], ['Needs Review', 'Needs review']]) {
    const g = by(v); if (!g.length) continue;
    h += groupHead(v, label, g.length, v) + (L.open[v] ? `<div class="panel" style="padding:4px 8px">${lookupTable(g)}</div>` : '');
  }
  const no = by('Not Eligible');
  if (no.length) {
    h += groupHead('Not Eligible', 'Cannot buy', no.length, 'Not Eligible');
    if (L.open['Not Eligible']) {
      const reasons = [...new Set(no.map((c) => c.decisive))].sort((a, b) => no.filter((c) => c.decisive === b).length - no.filter((c) => c.decisive === a).length);
      h += `<div style="display:grid; grid-template-columns:minmax(0,1fr); gap:6px; padding-left:18px">${reasons.map((k) => {
        const g = no.filter((c) => c.decisive === k), key = 'sub:' + k;
        return `<div class="row" style="padding:8px 12px; background:var(--panel); border:1px solid var(--line); border-radius:8px; cursor:pointer" onclick="LK.toggle('${key}')">
            <span>${L.open[key] ? '▾' : '▸'}</span>${oc('F')}<b>${RULE_LABELS[k]}</b><span class="muted">${g.length} Share Class${g.length > 1 ? 'es' : ''}</span></div>
          ${L.open[key] ? `<div class="panel" style="padding:4px 8px">${lookupTable(g)}</div>` : ''}`;
      }).join('')}</div>`;
    }
  }
  if (!rows.length) h += '<div class="panel muted">No Share Class matches.</div>';
  return h;
}

App.screens.dealer = { A: dealerA, B: dealerB, C: () => { dealerC(); const L = D.s.lk, el = document.getElementById('lk-q'); if (L?.focus && el) { el.focus(); el.setSelectionRange(el.value.length, el.value.length); } } };
