// Application shell: sign-in / register, role-based navigation, hash router.
import { api, refreshCsrf, resetSession } from './api.js';
import { h, replace } from './dom.js';
import { badge, card, errorMessage, fieldRow, toast } from './ui.js';
import { customerRoutes } from './customer.js';
import { adminRoutes } from './admin.js';

const app = document.getElementById('app');
let user = null;
let renderToken = 0;

const homeFor = (u) => (u.userType === 'ADMIN' ? '/a/overview' : '/c/overview');

function matchRoute(routes, path) {
  for (const route of routes) {
    const names = [];
    const re = new RegExp('^' + route.path.replace(/:[a-zA-Z]+/g, (m) => { names.push(m.slice(1)); return '([^/]+)'; }) + '$');
    const m = re.exec(path);
    if (m) {
      const params = {};
      names.forEach((n, i) => { params[n] = decodeURIComponent(m[i + 1]); });
      return { route, params };
    }
  }
  return null;
}

function currentLocation() {
  const raw = location.hash.replace(/^#/, '');
  const [path, qs = ''] = raw.split('?');
  return { path, query: new URLSearchParams(qs) };
}

async function render() {
  if (!user) { renderAuth(); return; }
  const routes = user.userType === 'ADMIN' ? adminRoutes : customerRoutes;
  const { path, query } = currentLocation();
  const found = matchRoute(routes, path);
  if (!found) { location.replace('#' + homeFor(user)); return; }

  const token = ++renderToken;
  const main = h('main', { class: 'content', id: 'main' }, h('div', { class: 'loading-block' }, 'Loading…'));
  replace(app, shell(routes, found.route, main));
  document.title = found.route.title + ' · Credit Card Billing';
  try {
    const node = await found.route.render({ ...found.params, params: query });
    if (token === renderToken) replace(main, node);
  } catch (e) {
    if (token === renderToken) replace(main, card('Could not load this page', h('div', { class: 'empty error' }, errorMessage(e))));
  }
}

function shell(routes, active, main) {
  const links = routes.filter((r) => r.label).map((r) => h('a', {
    href: '#' + r.path, class: 'nav-link',
  }, r.label));
  const activeLink = links.find((l) => l.getAttribute('href') === '#' + active.path)
    || links.find((l) => active.path.startsWith(l.getAttribute('href').slice(1) + '/'));
  if (activeLink) activeLink.classList.add('active');
  return h('div', { class: 'layout' },
    h('header', { class: 'topbar' },
      h('div', { class: 'brand' }, h('span', { class: 'logo' }), 'Credit Card Billing'),
      h('div', { class: 'who' }, h('span', null, user.displayName), badge(user.userType === 'ADMIN' ? 'ADMIN' : 'CUSTOMER'),
        h('button', { class: 'btn small', type: 'button', onclick: logout }, 'Sign out'))),
    h('div', { class: 'body' },
      h('nav', { class: 'sidebar', 'aria-label': 'Main' }, links),
      h('div', { class: 'main-col' }, h('h1', { class: 'page-title' }, active.title), main)));
}

async function logout() {
  try { await api.post('/api/auth/logout'); } catch { /* session may already be gone */ }
  resetSession();
  user = null;
  location.hash = '';
  render();
}

// ---------- sign in / register ----------
function renderAuth() {
  let type = 'CUSTOMER';
  let mode = 'login';
  const box = h('div', { class: 'auth-box' });
  const tab = (label, on, click) => h('button', { type: 'button', role: 'tab', class: 'tab' + (on ? ' on' : ''), 'aria-selected': on, onclick: click }, label);
  draw();
  replace(app, h('div', { class: 'auth-page' }, h('div', { class: 'auth-card' },
    h('div', { class: 'brand big' }, h('span', { class: 'logo' }), 'Credit Card Billing'),
    h('p', { class: 'muted center' }, 'Statements, payments and billing rules'), box)));
  document.title = 'Sign in · Credit Card Billing';

  function draw() {
    const tabs = h('div', { class: 'tabs', role: 'tablist' },
      tab('Customer', type === 'CUSTOMER', () => { type = 'CUSTOMER'; mode = 'login'; draw(); }),
      tab('Administrator', type === 'ADMIN', () => { type = 'ADMIN'; mode = 'login'; draw(); }));
    replace(box, tabs, type === 'ADMIN' || mode === 'login' ? loginForm() : registerForm());
  }

  async function enter(u) {
    await refreshCsrf(); // new session after login: fetch a fresh token
    user = u;
    location.hash = '#' + homeFor(u);
    render();
  }

  function loginForm() {
    const id = h('input', { name: 'identifier', type: type === 'ADMIN' ? 'text' : 'email', required: true, autocomplete: 'username' });
    const pw = h('input', { name: 'password', type: 'password', required: true, autocomplete: 'current-password' });
    const err = h('div', { class: 'form-error', role: 'alert' });
    const btn = h('button', { class: 'btn primary block', type: 'submit' }, 'Sign in');
    const form = h('form', { class: 'form', onsubmit: async (e) => {
      e.preventDefault(); err.textContent = ''; btn.disabled = true;
      try { await enter(await api.post('/api/auth/login', { userType: type, identifier: id.value.trim(), password: pw.value })); } catch (ex) { err.textContent = errorMessage(ex); } finally { btn.disabled = false; }
    } },
    fieldRow(type === 'ADMIN' ? 'Username' : 'Email', id), fieldRow('Password', pw), err, btn,
    type === 'CUSTOMER' ? h('p', { class: 'muted center' }, 'New here? ', h('a', { href: '#', onclick: (e) => { e.preventDefault(); mode = 'register'; draw(); } }, 'Create an account')) : null,
    type === 'ADMIN' ? h('p', { class: 'muted small' }, 'On a fresh local start the server console prints a one-time administrator password (or use BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD).') : null);
    return form;
  }

  function registerForm() {
    const f = {
      fullName: h('input', { name: 'fullName', required: true, maxlength: 120, autocomplete: 'name' }),
      email: h('input', { name: 'email', type: 'email', required: true, autocomplete: 'email' }),
      phone: h('input', { name: 'phone', placeholder: '+919876543210', autocomplete: 'tel' }),
      password: h('input', { name: 'password', type: 'password', required: true, minlength: 10, autocomplete: 'new-password' }),
    };
    const rows = {
      fullName: fieldRow('Full name', f.fullName), email: fieldRow('Email', f.email), phone: fieldRow('Phone (optional)', f.phone),
      password: fieldRow('Password', f.password, 'At least 10 characters, with a letter and a digit'),
    };
    const err = h('div', { class: 'form-error', role: 'alert' });
    const btn = h('button', { class: 'btn primary block', type: 'submit' }, 'Create account');
    return h('form', { class: 'form', onsubmit: async (e) => {
      e.preventDefault(); err.textContent = ''; btn.disabled = true;
      Object.values(rows).forEach((r) => { r.querySelector('.field-error').textContent = ''; });
      const body = { fullName: f.fullName.value.trim(), email: f.email.value.trim(), password: f.password.value };
      if (f.phone.value.trim()) body.phone = f.phone.value.trim();
      try {
        await api.post('/api/auth/register', body);
        toast('Account created. Signing you in…', 'success');
        await enter(await api.post('/api/auth/login', { userType: 'CUSTOMER', identifier: body.email, password: body.password }));
      } catch (ex) {
        if (ex.fieldErrors) Object.entries(ex.fieldErrors).forEach(([k, v]) => { if (rows[k]) rows[k].querySelector('.field-error').textContent = v; });
        err.textContent = errorMessage(ex);
      } finally { btn.disabled = false; }
    } },
    rows.fullName, rows.email, rows.phone, rows.password, err, btn,
    h('p', { class: 'muted center' }, 'Already registered? ', h('a', { href: '#', onclick: (e) => { e.preventDefault(); mode = 'login'; draw(); } }, 'Sign in')));
  }
}

// ---------- boot ----------
window.addEventListener('hashchange', () => { if (user) render(); });
window.addEventListener('app-refresh', () => { if (user) render(); });
window.addEventListener('session-expired', () => {
  if (!user) return;
  user = null;
  resetSession();
  toast('Your session has expired. Please sign in again.', 'warn');
  render();
});

(async function boot() {
  try {
    user = await api.get('/api/auth/me');
  } catch {
    user = null;
  }
  render();
})();
