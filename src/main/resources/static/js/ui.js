// Shared UI building blocks: toasts, modals, forms, paged tables, badges.
import { h, append, replace, clear } from './dom.js';
import { ApiError } from './api.js';

// ---------- toasts ----------
export function toast(message, type = 'info', ms = 4500) {
  const box = document.getElementById('toasts');
  if (!box) return;
  const el = h('div', { class: 'toast ' + type, role: 'status' }, message);
  box.append(el);
  setTimeout(() => el.remove(), ms);
}

export function errorMessage(e) {
  if (e instanceof ApiError) {
    if (e.fieldErrors) {
      const parts = Object.entries(e.fieldErrors).map(([k, v]) => k + ': ' + v);
      return e.message + (parts.length ? ' (' + parts.join('; ') + ')' : '');
    }
    return e.message;
  }
  return 'Something went wrong. Is the server running?';
}

export function reportError(e) {
  toast(errorMessage(e), 'error', 7000);
}

// ---------- badges ----------
const GOOD = ['ACTIVE', 'COMPLETED', 'PAID', 'POSTED', 'UP', 'ADMIN'];
const WARN = ['BLOCKED', 'PARTIALLY_PAID', 'UNPAID'];
const BAD = ['CLOSED', 'DISABLED', 'REVERSED', 'FAILED'];

export function badge(text) {
  if (text === null || text === undefined) return '—';
  const cls = GOOD.includes(text) ? 'good' : WARN.includes(text) ? 'warn' : BAD.includes(text) ? 'bad' : 'muted';
  return h('span', { class: 'badge ' + cls }, String(text).replaceAll('_', ' '));
}

// ---------- modal ----------
export function openModal({ title, content, wide = false }) {
  const overlay = h('div', { class: 'overlay' });
  const close = () => {
    overlay.remove();
    document.removeEventListener('keydown', onKey);
  };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  const box = h('div', { class: 'modal' + (wide ? ' wide' : ''), role: 'dialog', 'aria-modal': 'true', 'aria-label': title },
    h('div', { class: 'modal-head' }, h('h3', null, title), h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Close', onclick: close }, '×')),
    h('div', { class: 'modal-body' }, content));
  overlay.append(box);
  overlay.addEventListener('mousedown', (e) => { if (e.target === overlay) close(); });
  document.addEventListener('keydown', onKey);
  document.body.append(overlay);
  const first = box.querySelector('input, select, textarea, button.primary');
  if (first) first.focus();
  return { close, el: box };
}

export function confirmDialog({ title, message, confirmLabel = 'Confirm', danger = false }) {
  return new Promise((resolve) => {
    let done = false;
    const finish = (v) => { if (!done) { done = true; modal.close(); resolve(v); } };
    const modal = openModal({
      title,
      content: [
        h('p', null, message),
        h('div', { class: 'actions' },
          h('button', { class: 'btn', type: 'button', onclick: () => finish(false) }, 'Cancel'),
          h('button', { class: 'btn ' + (danger ? 'danger' : 'primary'), type: 'button', onclick: () => finish(true) }, confirmLabel)),
      ],
    });
    modal.el.parentElement.addEventListener('mousedown', (e) => { if (e.target === modal.el.parentElement) finish(false); });
  });
}

// ---------- forms ----------
/** A labelled form row. `input` is any element. */
export function fieldRow(label, input, hint) {
  return h('label', { class: 'field' }, h('span', { class: 'label' }, label), input,
    hint ? h('small', { class: 'hint' }, hint) : null, h('small', { class: 'field-error' }));
}

function buildInput(f, value) {
  const common = { name: f.name, id: 'f-' + f.name, placeholder: f.placeholder, required: !!f.required, readonly: !!f.readonly, autocomplete: f.autocomplete };
  const v = value === undefined || value === null ? '' : String(value);
  switch (f.type) {
    case 'select': {
      const sel = h('select', { ...common, readonly: null, disabled: f.readonly ? true : null },
        ...(f.options || []).map((o) => h('option', { value: o.value, selected: String(o.value) === v }, o.label)));
      return sel;
    }
    case 'textarea':
      return h('textarea', { ...common, rows: 3 }, v);
    case 'password':
      return h('input', { ...common, type: 'password', value: v, minlength: f.minLength, autocomplete: f.autocomplete || 'new-password' });
    case 'date':
      return h('input', { ...common, type: 'date', value: v });
    case 'int':
      return h('input', { ...common, type: 'number', step: 1, min: f.min, max: f.max, value: v });
    case 'decimal':
      return h('input', { ...common, type: 'number', step: f.step || '0.01', min: f.min, max: f.max, value: v, inputmode: 'decimal' });
    case 'email':
      return h('input', { ...common, type: 'email', value: v });
    default:
      return h('input', { ...common, type: 'text', value: v, maxlength: f.maxLength });
  }
}

function readValue(f, input) {
  let raw = input.value;
  if (typeof raw === 'string') raw = raw.trim();
  if (raw === '' || raw === null) return undefined;
  if (f.type === 'int' || f.numeric) return Number(raw);
  return raw; // decimals stay strings: no floating point error on the wire
}

/**
 * Builds a form inside a modal. onSubmit(values) may throw an ApiError; its field errors are shown inline.
 */
export function formModal({ title, fields, initial = {}, submitLabel = 'Save', onSubmit, wide = false, intro }) {
  const inputs = {};
  const rows = fields.map((f) => {
    const input = buildInput(f, initial[f.name] !== undefined ? initial[f.name] : f.value);
    inputs[f.name] = input;
    const row = fieldRow(f.label + (f.required ? ' *' : ''), input, f.hint);
    row.dataset.field = f.name;
    return row;
  });
  const banner = h('div', { class: 'form-error', role: 'alert' });
  const submit = h('button', { class: 'btn primary', type: 'submit' }, submitLabel);
  const form = h('form', { class: 'form', novalidate: true },
    intro ? h('p', { class: 'muted' }, intro) : null,
    h('div', { class: wide ? 'grid2' : 'stack' }, rows),
    banner,
    h('div', { class: 'actions' }, h('button', { class: 'btn', type: 'button', onclick: () => modal.close() }, 'Cancel'), submit));
  const modal = openModal({ title, content: form, wide });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    banner.textContent = '';
    form.querySelectorAll('.field-error').forEach((n) => { n.textContent = ''; });
    const values = {};
    for (const f of fields) values[f.name] = readValue(f, inputs[f.name]);
    const missing = fields.filter((f) => f.required && values[f.name] === undefined);
    if (missing.length) {
      missing.forEach((f) => { form.querySelector(`[data-field="${f.name}"] .field-error`).textContent = 'Required'; });
      return;
    }
    submit.disabled = true;
    try {
      await onSubmit(values, modal);
      modal.close();
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) {
        for (const [name, msg] of Object.entries(err.fieldErrors)) {
          const slot = form.querySelector(`[data-field="${name}"] .field-error`);
          if (slot) slot.textContent = msg;
        }
      }
      banner.textContent = errorMessage(err);
    } finally {
      submit.disabled = false;
    }
  });
  return modal;
}

// ---------- tables ----------
export function simpleTable(columns, rows, emptyText = 'Nothing here yet.') {
  if (!rows.length) return h('div', { class: 'empty' }, emptyText);
  return h('div', { class: 'table-scroll' }, h('table', { class: 'table' },
    h('thead', null, h('tr', null, columns.map((c) => h('th', { class: c.align === 'right' ? 'num' : '' }, c.label)))),
    h('tbody', null, rows.map((row) => h('tr', null, columns.map((c) =>
      h('td', { class: c.align === 'right' ? 'num' : '' }, c.render ? c.render(row) : row[c.key])))))));
}

/**
 * Server-paged table. load(page, size) -> PageResponse | array. Returns {el, reload(resetPage)}.
 */
export function dataTable({ columns, load, size = 10, emptyText = 'Nothing here yet.' }) {
  const wrap = h('div', { class: 'table-wrap' });
  let page = 0;
  let pageSize = size;

  async function reload(resetPage = false) {
    if (resetPage) page = 0;
    wrap.classList.add('loading');
    try {
      let res = await load(page, pageSize);
      if (Array.isArray(res)) {
        res = { content: res, page: 0, size: res.length || 1, totalElements: res.length, totalPages: 1 };
      }
      draw(res);
    } catch (e) {
      replace(wrap, h('div', { class: 'empty error' }, errorMessage(e)));
    } finally {
      wrap.classList.remove('loading');
    }
  }

  function draw(res) {
    const pager = h('div', { class: 'pager' },
      h('span', { class: 'muted' }, res.totalElements + ' record' + (res.totalElements === 1 ? '' : 's')
        + (res.totalPages > 1 ? ' · page ' + (res.page + 1) + ' of ' + res.totalPages : '')),
      h('span', { class: 'pager-btns' },
        h('select', { 'aria-label': 'Rows per page', onchange: (e) => { pageSize = Number(e.target.value); reload(true); } },
          [10, 20, 50].map((n) => h('option', { value: n, selected: n === pageSize }, n + ' / page'))),
        h('button', { class: 'btn small', type: 'button', disabled: res.page <= 0, onclick: () => { page = res.page - 1; reload(); } }, 'Prev'),
        h('button', { class: 'btn small', type: 'button', disabled: res.page + 1 >= res.totalPages, onclick: () => { page = res.page + 1; reload(); } }, 'Next')));
    replace(wrap, simpleTable(columns, res.content, emptyText), res.totalPages > 1 || res.totalElements > 10 ? pager : null);
  }

  reload();
  return { el: wrap, reload };
}

// ---------- misc layout helpers ----------
export function card(title, body, actions) {
  return h('section', { class: 'card' },
    (title || actions) ? h('div', { class: 'card-head' }, title ? h('h2', null, title) : null, actions ? h('div', { class: 'card-actions' }, actions) : null) : null,
    body);
}

export function kv(pairs) {
  return h('dl', { class: 'kv' }, pairs.filter(Boolean).map(([k, v]) => [h('dt', null, k), h('dd', null, v === undefined || v === null || v === '' ? '—' : v)]));
}

export function tile(label, value, sub) {
  return h('div', { class: 'tile' }, h('div', { class: 'tile-label' }, label), h('div', { class: 'tile-value' }, value),
    sub ? h('div', { class: 'tile-sub' }, sub) : null);
}

export function filterBar(fields, onChange) {
  const els = {};
  const bar = h('form', { class: 'filters', onsubmit: (e) => { e.preventDefault(); onChange(values()); } },
    fields.map((f) => {
      els[f.name] = f.type === 'select'
        ? h('select', { name: f.name, 'aria-label': f.label }, (f.options || []).map((o) => h('option', { value: o.value }, o.label)))
        : h('input', { name: f.name, type: f.type || 'text', placeholder: f.label, 'aria-label': f.label });
      return els[f.name];
    }),
    h('button', { class: 'btn small primary', type: 'submit' }, 'Filter'),
    h('button', { class: 'btn small', type: 'button', onclick: () => { Object.values(els).forEach((e) => { e.value = ''; }); onChange(values()); } }, 'Reset'));
  function values() {
    return Object.fromEntries(Object.entries(els).map(([k, e]) => [k, e.value]));
  }
  return bar;
}

export { h, append, replace, clear };
