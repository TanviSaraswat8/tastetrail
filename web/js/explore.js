// Explore: Mood Match (3-tap quiz + spin-the-wheel) and the Budget Feast Planner
const MOODS = [['comfort', '🛋️', 'Comfort'], ['healthy', '🥗', 'Light & fresh'], ['adventurous', '🧭', 'Adventurous'], ['celebrate', '🎉', 'Celebration'], ['quick', '⚡', 'Quick bite']];
const SPICE = [[0, '😌', 'Mild'], [1, '🙂', 'Medium'], [2, '🔥', 'Bring the heat']];
const BUDGETS = [[150, 'Under ₹150'], [250, 'Under ₹250'], [400, 'Under ₹400'], [2000, 'No limit']];
const S = { mood: 'comfort', spice: 1, budget: 400, veg: false, people: 4, total: 1000, pveg: false, matches: [], places: {} };
let angle = 0;

$('#page').innerHTML = `
  <h1>✨ Explore</h1>
  <p class="muted" style="margin-top:4px">Features you won't find on other food apps – built for the moments you can't decide.</p>
  <div class="xtabs"><a href="#mood" data-tab="mood">🎡 Mood Match</a><a href="#plan" data-tab="plan">🧮 Feast Planner</a><a href="passport.html">🎖️ Taste Passport</a></div>

  <section id="tab-mood" class="grid" style="grid-template-columns:1.25fr 1fr;gap:22px;align-items:start">
    <div class="card pad quiz">
      <div><h2>What are you in the mood for?</h2><p class="small muted">Three taps and we'll match dishes to your mood, spice level and budget.</p></div>
      <div class="q"><b>1 · Mood</b><div class="opts" id="o-mood"></div></div>
      <div class="q"><b>2 · Spice level</b><div class="opts" id="o-spice"></div></div>
      <div class="q"><b>3 · Budget per dish</b><div class="opts" id="o-budget"></div></div>
      <label class="row small" style="gap:8px"><input type="checkbox" id="m-veg"> 🌿 Vegetarian only</label>
      <div id="m-res" class="grid" style="gap:10px"></div>
    </div>
    <div class="card pad" style="text-align:center;position:sticky;top:84px">
      <h2>Still can't decide?</h2><p class="small muted">Spin the wheel – it picks from your top matches.</p>
      <div class="wheel-wrap"><div class="wheel-pin">🔻</div><canvas id="wheel" width="600" height="600"></canvas><button class="wheel-hub" id="spin" title="Spin">🎲</button></div>
      <button class="btn btn-primary" id="spin2" style="margin-top:16px">Spin the wheel</button>
      <div id="spun" style="margin-top:14px;min-height:60px"></div>
    </div>
  </section>

  <section id="tab-plan" class="hidden">
    <div class="card pad" style="display:grid;grid-template-columns:auto 1fr auto;gap:26px;align-items:end">
      <div><b class="small">How many people?</b><div class="stepper" style="margin-top:6px"><button id="p-dec">−</button><span id="p-n">4</span><button id="p-inc">+</button></div></div>
      <div><div class="row between"><b class="small">Total budget (incl. taxes & delivery)</b><b id="b-v" style="font-size:20px;color:var(--brand-deep)">₹1,000</b></div>
        <input type="range" id="b-r" min="200" max="5000" step="50" value="1000" style="margin-top:10px">
        <div class="row xs muted between"><span>₹200</span><span>₹5,000</span></div></div>
      <div class="stack" style="gap:8px"><label class="row small" style="gap:8px"><input type="checkbox" id="p-veg"> 🌿 Veg only</label>
        <button class="btn btn-primary" id="plan-go">🧮 Plan my feast</button></div>
    </div>
    <p class="xs muted" style="margin:10px 2px 16px">We try every combination of dishes (dynamic programming) and pick the most satisfying meal where everyone gets a full serving and the final bill stays inside your budget.</p>
    <div id="plans" class="grid g3"></div>
  </section>`;

const optBtns = (el, list, key) => {
  $(el).innerHTML = list.map(([v, e, l]) => `<button class="opt ${S[key] === v ? 'on' : ''}" data-k="${key}" data-v="${v}">${l === undefined ? e : `<span class="oe">${e}</span>${l}`}</button>`).join('');
};
function renderQuiz() {
  optBtns('#o-mood', MOODS, 'mood'); optBtns('#o-spice', SPICE, 'spice');
  $('#o-budget').innerHTML = BUDGETS.map(([v, l]) => `<button class="opt ${S.budget === v ? 'on' : ''}" data-k="budget" data-v="${v}">${l}</button>`).join('');
}
document.addEventListener('click', (e) => {
  const b = e.target.closest('.opt[data-k]');
  if (!b) return;
  const k = b.dataset.k; S[k] = k === 'mood' ? b.dataset.v : Number(b.dataset.v);
  renderQuiz(); findMatches();
});
$('#m-veg').onchange = (e) => { S.veg = e.target.checked; findMatches(); };

const restaurantOf = (d) => S.places[d.restaurant_id] || { id: d.restaurant_id, name: d.restaurant, area: '', theme: 'amber' };
function addDish(d) {
  Cart.setQty(d, restaurantOf(d), Cart.qtyOf(d.id) + 1);
  toast(`Added ${d.name} 🛍️`);
}

async function findMatches() {
  $('#m-res').innerHTML = '<p class="small muted">Matching…</p>';
  try {
    const r = await api(`/explore/mood?mood=${S.mood}&spice=${S.spice}&budget=${S.budget}&veg=${S.veg ? 1 : ''}`);
    S.matches = r.matches;
    $('#m-res').innerHTML = !r.matches.length ? '<p class="small muted">Nothing matches – try a bigger budget or another mood.</p>'
      : `<h3 style="margin-top:4px">${r.emoji} Your ${esc(r.mood.toLowerCase())} matches</h3>` + r.matches.map((d, i) => `
      <div class="match" style="animation-delay:${i * 40}ms">${dishPic(d, 72)}
        <div style="flex:1;min-width:0"><div class="row between" style="align-items:flex-start"><div style="min-width:0"><div class="row" style="gap:6px">${vegMark(d.is_veg)}<b class="truncate">${esc(d.name)}</b></div>
          <div class="xs muted"><a href="restaurant.html?id=${d.restaurant_id}" style="text-decoration:underline">${esc(d.restaurant)}</a> · ${inr(d.price)} · ~${d.eta_minutes} min</div></div>
          <button class="add" data-madd="${i}">ADD</button></div>
          <div class="reasons">${d.reasons.map((x) => `<span>${esc(x)}</span>`).join('')}</div></div></div>`).join('');
    drawWheel();
  } catch (e) { showError($('#m-res'), e.message); }
}
document.addEventListener('click', (e) => {
  const b = e.target.closest('[data-madd]');
  if (b) addDish(S.matches[Number(b.dataset.madd)]);
});

// ---------- spin the wheel ----------
const COLORS = ['#f2622e', '#f59e0b', '#16a34a', '#0ea5e9', '#8b5cf6', '#ec4899', '#14b8a6', '#ef4444'];
function drawWheel() {
  const items = S.matches.slice(0, 8), cv = $('#wheel'), x = cv.getContext('2d'), R = 300;
  x.clearRect(0, 0, 600, 600);
  if (!items.length) return;
  const seg = (2 * Math.PI) / items.length;
  items.forEach((d, i) => {
    x.beginPath(); x.moveTo(R, R); x.arc(R, R, R - 4, i * seg - Math.PI / 2, (i + 1) * seg - Math.PI / 2); x.closePath();
    x.fillStyle = COLORS[i % COLORS.length]; x.fill(); x.strokeStyle = '#fff'; x.lineWidth = 6; x.stroke();
    x.save(); x.translate(R, R); x.rotate(i * seg + seg / 2 - Math.PI / 2);
    x.textAlign = 'right'; x.fillStyle = '#fff'; x.font = '700 26px Inter, Segoe UI, sans-serif';
    const name = d.name.replace(/\s*\(.*\)/, ''); x.fillText(name.length > 15 ? name.slice(0, 14) + '…' : name, R - 30, 9);
    x.font = '38px serif'; x.fillText(d.emoji || '🍽️', R - 30 - x.measureText('').width - 0, -26); x.restore();
  });
}
function spin() {
  const items = S.matches.slice(0, 8);
  if (!items.length) return;
  const pick = Math.floor(Math.random() * items.length), seg = 360 / items.length;
  // the pin is at the top: rotate so the middle of the chosen segment ends there
  const target = 360 - (pick * seg + seg / 2);
  angle += 360 * 5 + ((target - (angle % 360)) + 360) % 360;
  $('#wheel').style.transform = `rotate(${angle}deg)`;
  $('#spun').innerHTML = '<p class="small muted">Spinning… 🥁</p>';
  $('#spin').disabled = $('#spin2').disabled = true;
  setTimeout(() => {
    const d = items[pick];
    $('#spin').disabled = $('#spin2').disabled = false;
    $('#spun').innerHTML = `<div class="match" style="text-align:left">${dishPic(d, 64)}<div style="flex:1"><div class="xs muted">The wheel has spoken 🎉</div><b>${esc(d.name)}</b>
      <div class="xs muted">${esc(d.restaurant)} · ${inr(d.price)}</div></div><button class="btn btn-primary" data-madd="${pick}">Add</button></div>`;
  }, 4100);
}
$('#spin').onclick = spin; $('#spin2').onclick = spin;

// ---------- feast planner ----------
const setPeople = (n) => { S.people = Math.max(1, Math.min(12, n)); $('#p-n').textContent = S.people; };
$('#p-dec').onclick = () => setPeople(S.people - 1);
$('#p-inc').onclick = () => setPeople(S.people + 1);
$('#b-r').oninput = (e) => { S.total = Number(e.target.value); $('#b-v').textContent = inr(S.total); };
$('#p-veg').onchange = (e) => { S.pveg = e.target.checked; };
let lastPlans = [];
$('#plan-go').onclick = async () => {
  const btn = $('#plan-go'); btn.disabled = true; btn.textContent = 'Planning…';
  try {
    const r = await api(`/explore/plan?people=${S.people}&budget=${S.total}&veg=${S.pveg ? 1 : ''}`);
    lastPlans = r.plans;
    $('#plans').innerHTML = !r.plans.length
      ? `<div class="card pad" style="grid-column:1/-1"><b>That budget is too tight for ${S.people} ${S.people === 1 ? 'person' : 'people'}.</b><p class="small muted">Try at least ${inr(S.people * 160)}.</p></div>`
      : r.plans.map((p, i) => `
      <div class="plan ${i === 0 ? 'best' : ''}" style="animation-delay:${i * 80}ms">
        <div class="row between"><div><div class="xs muted">${i === 0 ? '🏆 Best value' : `Option ${i + 1}`}</div><h3>${esc(p.restaurant)}</h3></div>
          <span class="emoji-tile" style="width:44px;height:44px;font-size:24px">${RESTAURANT_EMOJI[p.restaurant] || '🍽️'}</span></div>
        <div class="stack" style="gap:6px">${p.items.map((d) => `<div class="pl">${dishPic(d, 38)}<span style="flex:1"><b>${d.quantity} ×</b> ${esc(d.name)}</span><span class="small muted">${inr(d.price * d.quantity)}</span></div>`).join('')}</div>
        <div class="small" style="border-top:1px solid var(--line);padding-top:8px">
          <div class="row between"><span class="muted">Food</span><span>${inr(p.food)}</span></div>
          <div class="row between"><b>Total to pay</b><b>${inr(p.total)}</b></div>
          <div class="row between xs muted"><span>🍽️ ${p.servings} servings for ${S.people}</span><span>${inr(p.spare)} left over</span></div>
          <div class="meter" style="margin-top:6px" title="Budget used"><i style="width:${Math.min(100, (p.total / S.total) * 100)}%"></i></div></div>
        <button class="btn btn-primary btn-block" data-plan="${i}">Add all to cart</button>
      </div>`).join('');
  } catch (e) { showError($('#plans'), e.message); }
  btn.disabled = false; btn.textContent = '🧮 Plan my feast';
};
document.addEventListener('click', (e) => {
  const b = e.target.closest('[data-plan]');
  if (!b) return;
  const p = lastPlans[Number(b.dataset.plan)];
  Cart.replaceWith(S.places[p.restaurant_id] || { id: p.restaurant_id, name: p.restaurant }, p.items, () => (location.href = 'cart.html'));
});

// ---------- tabs ----------
function showTab() {
  const t = location.hash === '#plan' ? 'plan' : 'mood';
  $('#tab-mood').classList.toggle('hidden', t !== 'mood');
  $('#tab-plan').classList.toggle('hidden', t !== 'plan');
  document.querySelectorAll('.xtabs [data-tab]').forEach((a) => a.classList.toggle('on', a.dataset.tab === t));
}
window.addEventListener('hashchange', showTab);
showTab(); renderQuiz();
api('/restaurants').then((rs) => { rs.forEach((r) => (S.places[r.id] = r)); }).catch(() => {});
findMatches();
