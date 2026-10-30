// Sign-in and registration page
let mode = 'login';

function render() {
  $('#page').innerHTML = `
  <div class="auth card">
    <div class="art">
      <div style="font-size:60px">🍱</div>
      <h2>Hot food from your favourite places, delivered.</h2>
      <p style="margin-top:12px;opacity:.88">Browse 8 restaurants across Noida, build a cart, apply offers and follow your order live.</p>
      <div class="grid g3" style="margin-top:40px">
        <div class="stat"><b>8</b><div class="small">restaurants</div></div>
        <div class="stat"><b>59</b><div class="small">dishes</div></div>
        <div class="stat"><b>~35 min</b><div class="small">avg delivery</div></div>
      </div>
    </div>
    <div style="padding:38px">
      <div class="tabs"><button data-mode="login" class="${mode === 'login' ? 'on' : ''}">Sign in</button><button data-mode="register" class="${mode === 'register' ? 'on' : ''}">Create account</button></div>
      <form id="form" class="stack" style="margin-top:22px">
        ${mode === 'register' ? `<input class="input" name="name" placeholder="Full name" required>
        <input class="input" name="phone" placeholder="Mobile number (10 digits)">` : ''}
        <input class="input" name="email" type="email" placeholder="Email address" required>
        <input class="input" name="password" type="password" placeholder="Password" required>
        <div id="err"></div>
        <button class="btn btn-primary btn-block">${mode === 'login' ? 'Sign in' : 'Create account'}</button>
      </form>
      <div style="margin-top:28px;border-top:1px solid var(--line);padding-top:18px">
        <p class="xs bold muted" style="letter-spacing:.5px;margin-bottom:10px">DEMO ACCOUNTS</p>
        <div class="grid g2">
          <button class="btn btn-ghost" data-demo="customer">Customer demo</button>
          <button class="btn btn-ghost" data-demo="admin">Admin demo</button>
        </div>
      </div>
    </div>
  </div>`;
  document.querySelectorAll('[data-mode]').forEach((b) => (b.onclick = () => { mode = b.dataset.mode; render(); }));
  document.querySelectorAll('[data-demo]').forEach((b) => (b.onclick = () => submit(b.dataset.demo === 'admin'
    ? { email: 'admin@tastetrail.in', password: 'admin123' } : { email: 'demo@tastetrail.in', password: 'demo123' }, true)));
  $('#form').onsubmit = (e) => { e.preventDefault(); submit(Object.fromEntries(new FormData(e.target)), false); };
}

async function submit(body, isDemo) {
  showError($('#err'), '');
  try {
    const data = await api(mode === 'login' || isDemo ? '/auth/login' : '/auth/register', { method: 'POST', body });
    Auth.save(data);
    location.href = data.user.role === 'admin' ? 'admin.html' : (qs('next') || 'index.html');
  } catch (e) { showError($('#err'), e.message); }
}

render();
