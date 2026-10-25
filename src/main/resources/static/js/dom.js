// Tiny DOM + formatting helpers. Content is always inserted as text nodes (never innerHTML), so API data cannot inject markup.

export function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value === null || value === undefined || value === false) continue;
    if (key === 'class') el.className = value;
    else if (key.startsWith('on') && typeof value === 'function') el.addEventListener(key.slice(2).toLowerCase(), value);
    else if (value === true) el.setAttribute(key, '');
    else el.setAttribute(key, String(value));
  }
  append(el, children);
  return el;
}

export function append(el, children) {
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}

export function clear(el) {
  while (el.firstChild) el.removeChild(el.firstChild);
  return el;
}

export function replace(el, ...children) {
  clear(el);
  return append(el, children);
}

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' });

export function money(value) {
  if (value === null || value === undefined || value === '') return '—';
  return inr.format(Number(value));
}

export function num(value, digits) {
  if (value === null || value === undefined) return '—';
  return Number(value).toLocaleString('en-IN', { maximumFractionDigits: digits ?? 4 });
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** "2026-03-15" -> "15 Mar 2026" (no time-zone conversion for pure dates). */
export function date(value) {
  if (!value) return '—';
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  return m ? `${Number(m[3])} ${MONTHS[Number(m[2]) - 1]} ${m[1]}` : String(value);
}

/** Server timestamps are UTC wall-clock values without a zone; show them in the viewer's locale. */
export function dateTime(value) {
  if (!value) return '—';
  const d = new Date(value.endsWith('Z') ? value : value + 'Z');
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
}

/** Business "today" is the UTC date (matches the server). */
export function todayUtc() {
  return new Date().toISOString().slice(0, 10);
}

export function newKey() {
  if (globalThis.crypto && crypto.randomUUID) return crypto.randomUUID();
  return 'k-' + Math.random().toString(36).slice(2) + Date.now().toString(36);
}

export function query(params) {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params || {})) {
    if (v !== undefined && v !== null && String(v).trim() !== '') q.set(k, String(v).trim());
  }
  const s = q.toString();
  return s ? '?' + s : '';
}

/** Ask the router to re-render the current page (after a successful change). */
export function refreshPage() {
  window.dispatchEvent(new CustomEvent('app-refresh'));
}
