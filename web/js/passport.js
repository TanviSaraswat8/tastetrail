// Taste Passport: cuisine stamps, XP levels and bronze/silver/gold badges, earned from real orders
const me = Auth.require('customer');
if (me) load();

async function load() {
  try {
    const p = await api('/me/passport');
    const lv = p.level, next = lv.nextXp;
    const pct = next ? Math.round(((p.xp - lv.fromXp) / (next - lv.fromXp)) * 100) : 100;
    $('#page').innerHTML = `
      <div class="stack">
        <section class="passport-hero">
          <div class="row between wrap" style="align-items:flex-start">
            <div><div class="lvl">Level ${lv.number} of 5</div><h1 style="font-size:30px;margin-top:2px">🎖️ ${esc(lv.name)}</h1>
              <p style="opacity:.85;margin-top:4px">${esc(me.name.split(' ')[0])}'s Taste Passport · ${p.ordersCount} delivered orders</p></div>
            <div style="text-align:right"><div style="font-size:34px;font-weight:800;line-height:1">${p.xp.toLocaleString('en-IN')}</div><div class="small" style="opacity:.85">XP</div></div>
          </div>
          <div class="xpbar"><i style="width:0%" id="xpfill"></i></div>
          <p class="xs" style="opacity:.85;margin-top:6px">${next ? `${(next - p.xp).toLocaleString('en-IN')} XP to the next level` : 'Top level reached!'} · +10 XP per order, +25 for a new cuisine, +5 for rating it</p>
        </section>

        <section class="challenge">
          <span style="font-size:34px">🧭</span>
          <div style="flex:1"><b>Next challenge</b><p class="small muted">${esc(p.challengeText)}</p></div>
          ${p.challenge ? `<a class="btn btn-primary" href="restaurant.html?id=${p.challenge.id}">Take it</a>` : ''}
        </section>

        <section>
          <div class="row between"><h2>Cuisine stamps</h2><span class="small muted"><b>${p.stampsCollected}</b> of ${p.stamps.length} collected</span></div>
          <p class="small muted">Order from a restaurant to stamp its cuisine. Grey stamps are still waiting for you.</p>
          <div class="stamps" style="margin-top:14px">${p.stamps.map((s) => `
            <a class="stamp ${s.collected ? 'got' : ''}" data-n="${s.orders}" href="restaurant.html?id=${s.restaurantId}" title="${esc(s.restaurant)} – ${esc(s.cuisine)}">
              <div><div class="se">${RESTAURANT_EMOJI[s.restaurant] || '🍽️'}</div><div class="sn">${esc(s.cuisine.split(' · ')[0])}</div></div></a>`).join('')}</div>
        </section>

        <section>
          <h2>Badges</h2><p class="small muted">Each badge has Bronze, Silver and Gold tiers.</p>
          <div class="badges" style="margin-top:14px">${p.badges.map((b) => `
            <div class="bdg t${b.tier}"><span class="bi">${b.emoji}</span>
              <div style="flex:1;min-width:0"><div class="row" style="gap:0"><b class="small">${esc(b.name)}</b>${b.tier ? `<span class="tier t${b.tier}">${b.tierName.toUpperCase()}</span>` : ''}</div>
                <div class="xs muted">${esc(b.description)}</div>
                <div class="row" style="gap:8px;margin-top:5px"><div class="meter" style="flex:1"><i style="width:${Math.round((b.progress / b.target) * 100)}%"></i></div>
                  <span class="xs muted num">${b.progress}/${b.target}</span></div></div></div>`).join('')}</div>
        </section>
        <p class="xs muted">Why a passport? Loyalty programmes on delivery apps reward how much you spend. TasteTrail rewards how much you explore.</p>
      </div>`;
    requestAnimationFrame(() => setTimeout(() => ($('#xpfill').style.width = pct + '%'), 60));
  } catch (e) { showError($('#page'), e.message); }
}
