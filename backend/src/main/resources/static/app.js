/* KR3 Web UI — vanilla JS + fetch against frozen REST contract (spec §4).
 * Tokens in localStorage (course assumption). No build step. */
'use strict';

const API = '';
const LS = { access: 'vp_access', refresh: 'vp_refresh', user: 'vp_user' };

const state = {
  user: JSON.parse(localStorage.getItem(LS.user) || 'null'),
  reqPage: 0, usersPage: 0, auditPage: 0,
  lastReqFilter: null, lastAuditFilter: null,
  currentReqId: null,
  employees: [], // cached GET /api/employees (active) for dropdowns
  permanentDeleteEnabled: false,
};

function roles() { return (state.user && state.user.roles) || []; }
function isAdmin() { return roles().includes('ADMIN'); }
function canEdit() { return roles().includes('OPERATOR') || isAdmin(); }

function show(screen) {
  document.querySelectorAll('.screen').forEach(s => s.hidden = true);
  const el = document.getElementById('screen-' + screen);
  if (el) el.hidden = false;
  document.querySelectorAll('#tabs [data-screen]').forEach(b => {
    b.classList.toggle('active', b.dataset.screen === screen);
  });
}

function applyRoleVisibility() {
  const admin = isAdmin(), edit = canEdit();
  document.querySelectorAll('[data-role="ADMIN"]').forEach(el => {
    el.style.display = admin ? '' : 'none';
  });
  document.querySelectorAll('[data-role-op]').forEach(el => {
    el.hidden = !edit;
  });
  document.querySelectorAll('#tabs [data-screen]').forEach(b => {
    if (b.dataset.role === 'ADMIN' && !admin) b.style.display = 'none';
    else b.style.display = '';
  });
  applyDeletePermVisibility();
}

async function refreshConfig() {
  try {
    const res = await api('/api/config');
    const data = await jsonOrThrow(res, 'Конфиг');
    state.permanentDeleteEnabled = data.permanentDeleteEnabled === true;
  } catch (_) {
    state.permanentDeleteEnabled = false;
  }
  applyDeletePermVisibility();
}

function applyDeletePermVisibility() {
  const btn = document.getElementById('cardDeletePerm');
  if (btn) btn.style.display = (isAdmin() && state.permanentDeleteEnabled) ? '' : 'none';
}

function setLoggedIn(user) {
  state.user = user;
  localStorage.setItem(LS.user, JSON.stringify(user));
  document.getElementById('tabs').hidden = false;
  document.getElementById('logoutBtn').hidden = false;
  document.getElementById('changePwBtn').hidden = false;
  document.getElementById('userbox').hidden = false;
  document.getElementById('username').textContent = user.name || user.email || '';
  document.getElementById('userrole').textContent = '(' + roles().join(',') + ')';
  applyRoleVisibility();
  refreshConfig();
}

function logoutLocal() {
  Object.values(LS).forEach(k => localStorage.removeItem(k));
  state.user = null;
  document.getElementById('tabs').hidden = true;
  document.getElementById('logoutBtn').hidden = true;
  document.getElementById('changePwBtn').hidden = true;
  document.getElementById('changePwPanel').hidden = true;
  document.getElementById('userbox').hidden = true;
  show('login');
}

function needPasswordChange() {
  document.getElementById('mustChangeBox').hidden = false;
  document.getElementById('tabs').hidden = true;
  document.getElementById('userbox').hidden = true;
  show('login');
}

async function api(path, opts = {}) {
  const headers = Object.assign({}, opts.headers || {});
  const token = localStorage.getItem(LS.access);
  if (token) headers['Authorization'] = 'Bearer ' + token;
  if (opts.body !== undefined && !(opts.body instanceof FormData) && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }
  const cid = sessionStorage.getItem('vp_cid') ||
    (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()));
  sessionStorage.setItem('vp_cid', cid);
  headers['X-Correlation-ID'] = cid;
  let res = await fetch(API + path, Object.assign({}, opts, { headers }));
  if (res.status === 403) {
    let msg = '';
    try { msg = (await res.clone().json()).message || ''; } catch (_) { /* ignore */ }
    if (msg === 'Password change required') { needPasswordChange(); return res; }
  }
  if (res.status === 401 && !opts._retried && !path.startsWith('/api/auth/')) {
    const ok = await tryRefresh();
    if (ok) {
      const t2 = localStorage.getItem(LS.access);
      headers['Authorization'] = 'Bearer ' + t2;
      res = await fetch(API + path, Object.assign({}, opts, { headers, _retried: true }));
    } else {
      logoutLocal();
      throw new Error('Сессия истекла, войдите заново');
    }
  }
  return res;
}

async function tryRefresh() {
  const rt = localStorage.getItem(LS.refresh);
  if (!rt) return false;
  const res = await fetch(API + '/api/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: rt }),
  });
  if (!res.ok) return false;
  const data = await res.json();
  localStorage.setItem(LS.access, data.accessToken);
  localStorage.setItem(LS.refresh, data.refreshToken);
  return true;
}

async function jsonOrThrow(res, ctx) {
  if (!res.ok) {
    let msg = 'HTTP ' + res.status;
    try { const e = await res.json(); msg = e.message || msg; } catch (_) { /* ignore */ }
    if (res.status === 409) msg = 'Конфликт версий, обновите карточку: ' + msg;
    if (ctx) msg = ctx + ': ' + msg;
    throw new Error(msg);
  }
  if (res.status === 204) return null;
  const ct = res.headers.get('content-type') || '';
  return ct.includes('json') ? res.json() : res.text();
}

/* ---------- login ---------- */
document.getElementById('loginForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  const err = document.getElementById('loginError');
  err.textContent = '';
  err.className = 'error';
  try {
    const res = await fetch(API + '/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: fd.get('email'), password: fd.get('password') }),
    });
    const data = await jsonOrThrow(res, 'Вход');
    localStorage.setItem(LS.access, data.accessToken);
    localStorage.setItem(LS.refresh, data.refreshToken);
    if (data.mustChangePassword) {
      needPasswordChange();
    } else {
      setLoggedIn(data.user);
      document.getElementById('mustChangeBox').hidden = true;
      show('requests');
      loadRequests();
    }
  } catch (ex) { err.textContent = ex.message; }
});

document.getElementById('changePwForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  const err = document.getElementById('changePwError');
  err.textContent = '';
  try {
    const res = await api('/api/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({ oldPassword: fd.get('oldPassword'), newPassword: fd.get('newPassword') }),
    });
    await jsonOrThrow(res, 'Смена пароля');
    document.getElementById('mustChangeBox').hidden = true;
    e.target.reset();
    const relogin = await fetch(API + '/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: document.querySelector('#loginForm [name="email"]').value,
        password: fd.get('newPassword'),
      }),
    });
    const loginData = await jsonOrThrow(relogin, 'Вход');
    localStorage.setItem(LS.access, loginData.accessToken);
    localStorage.setItem(LS.refresh, loginData.refreshToken);
    const me = await api('/api/auth/me').then(r => jsonOrThrow(r, 'Профиль'));
    setLoggedIn(me);
    show('requests');
    loadRequests();
  } catch (ex) { err.textContent = ex.message; }
});

document.getElementById('logoutBtn').addEventListener('click', async () => {
  try {
    await api('/api/auth/logout', {
      method: 'POST',
      body: JSON.stringify({ refreshToken: localStorage.getItem(LS.refresh) }),
    });
  } catch (_) { /* ignore */ }
  logoutLocal();
});

document.getElementById('changePwBtn').addEventListener('click', () => {
  const p = document.getElementById('changePwPanel');
  p.hidden = !p.hidden;
  document.getElementById('changePwLoggedMsg').textContent = '';
});
document.getElementById('changePwCancel').addEventListener('click', () => {
  document.getElementById('changePwPanel').hidden = true;
});
document.getElementById('changePwLoggedForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  const msg = document.getElementById('changePwLoggedMsg');
  msg.textContent = '';
  msg.className = '';
  try {
    const res = await api('/api/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({ oldPassword: fd.get('oldPassword'), newPassword: fd.get('newPassword') }),
    });
    await jsonOrThrow(res, 'Смена пароля');
    e.target.reset();
    logoutLocal();
    const le = document.getElementById('loginError');
    le.className = 'ok';
    le.textContent = 'Пароль сменён, войдите заново';
  } catch (ex) { msg.textContent = ex.message; msg.className = 'error'; }
});

document.querySelectorAll('#tabs [data-screen]').forEach(b => {
  b.addEventListener('click', () => {
    const s = b.dataset.screen;
    show(s);
    if (s === 'requests') loadRequests();
    if (s === 'employees') loadEmployees();
    if (s === 'analytics') loadAnalytics();
    if (s === 'users') loadUsers();
    if (s === 'audit') loadAudit();
  });
});

/* ---------- employees cache (dropdowns) ---------- */
async function refreshEmployeeCache() {
  try {
    const res = await api('/api/employees?active=true');
    const list = await jsonOrThrow(res, 'Сотрудники');
    state.employees = Array.isArray(list) ? list : [];
  } catch (_) { state.employees = []; }
}

function fillEmployeeSelects() {
  const host = document.getElementById('reqHostSelect');
  host.innerHTML = '';
  for (const m of state.employees) {
    const o = document.createElement('option');
    o.value = m.id;
    o.textContent = m.fullName + ' (' + m.department + ')';
    host.appendChild(o);
  }
  const filt = document.getElementById('reqEmployeeFilter');
  const keep = filt.value;
  filt.innerHTML = '<option value="">Сотрудник: все</option>';
  for (const m of state.employees) {
    const o = document.createElement('option');
    o.value = m.id;
    o.textContent = m.fullName;
    filt.appendChild(o);
  }
  filt.value = keep || '';
}

/* ---------- requests list ---------- */
function currentReqQuery(page) {
  const fd = new FormData(document.getElementById('reqFilters'));
  const p = new URLSearchParams();
  for (const k of ['q', 'employeeId', 'status', 'escort', 'risk', 'tag', 'archived',
      'minGuests', 'maxGuests', 'sort', 'dir', 'size']) {
    const v = (fd.get(k) || '').toString().trim();
    if (v) p.set(k, v);
  }
  p.set('page', String(page));
  return p.toString();
}

async function loadRequests() {
  const err = document.getElementById('reqError');
  err.textContent = '';
  try {
    await refreshEmployeeCache();
    fillEmployeeSelects();
    const q = currentReqQuery(state.reqPage);
    state.lastReqFilter = q;
    const res = await api('/api/requests?' + q);
    const data = await jsonOrThrow(res, 'Заявки');
    const list = data.content || [];
    const tb = document.querySelector('#reqTable tbody');
    tb.innerHTML = '';
    for (const r of list) {
      const tr = document.createElement('tr');
      if (r.archived) tr.classList.add('archived');
      const host = (r.host && (r.host.fullName || r.host.id)) || '';
      const visitor = (r.visitor && r.visitor.fullName) || '';
      const mark = r.archived ? ' <span class="badge">ARCHIVED</span>' : '';
      tr.innerHTML = '<td><input type="checkbox" data-id="' + esc(r.id) + '"></td>' +
        '<td>' + esc(r.requestCode) + mark + '</td><td>' + esc(visitor) + '</td>' +
        '<td>' + esc(host) + '</td><td>' + esc(r.visitDate) + '</td>' +
        '<td>' + esc(r.status) + '</td><td>' + esc(r.guestCount) + '</td>' +
        '<td>' + esc(r.risk || '') + '</td><td><button>Открыть</button></td>';
      tr.querySelector('button').addEventListener('click', () => openCard(r.id));
      tb.appendChild(tr);
    }
    document.getElementById('reqPageInfo').textContent =
      'стр. ' + (state.reqPage + 1) +
      (data.totalPages ? ' из ' + data.totalPages : '') +
      (data.totalElements != null ? ' (всего ' + data.totalElements + ')' : '');
  } catch (ex) { err.textContent = ex.message; }
}

function esc(v) {
  return String(v == null ? '' : v).replace(/[&<>"']/g, c =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function el(f, n) { return f.elements.namedItem(n); }

document.getElementById('reqFilters').addEventListener('submit', (e) => {
  e.preventDefault();
  state.reqPage = 0;
  loadRequests();
});
document.getElementById('reqPrev').addEventListener('click', () => {
  if (state.reqPage > 0) { state.reqPage--; loadRequests(); }
});
document.getElementById('reqNext').addEventListener('click', () => {
  state.reqPage++; loadRequests();
});
document.getElementById('reqExportBtn').addEventListener('click', downloadExport);
document.getElementById('csvExportBtn').addEventListener('click', downloadExport);

async function downloadExport() {
  const err = document.getElementById('reqError');
  try {
    const q = state.lastReqFilter || currentReqQuery(0);
    const res = await api('/api/requests/export?' + q);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const blob = await res.blob();
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'requests.csv';
    a.click();
    URL.revokeObjectURL(a.href);
  } catch (ex) { err.textContent = ex.message; }
}

function selectedIds() {
  return [...document.querySelectorAll('#reqTable tbody input[type=checkbox]:checked')]
    .map(c => c.dataset.id);
}
document.getElementById('bulkAll').addEventListener('change', (e) => {
  document.querySelectorAll('#reqTable tbody input[type=checkbox]')
    .forEach(c => { c.checked = e.target.checked; });
});

function bulkResultText(data) {
  const ok = (data.succeeded || []).length;
  const fails = data.failed || [];
  let s = 'OK: ' + ok + ', ошибок: ' + fails.length;
  if (fails.length) s += '\n' + fails.map(f => f.id + ': ' + f.error).join('\n');
  return s;
}

document.querySelectorAll('#bulkBar [data-bulk]').forEach(b => {
  b.addEventListener('click', async () => {
    const err = document.getElementById('reqError');
    err.textContent = '';
    try {
      const ids = selectedIds();
      if (!ids.length) return;
      const res = await api('/api/requests/bulk', {
        method: 'POST',
        body: JSON.stringify({ ids, operation: b.dataset.bulk }),
      });
      const data = await jsonOrThrow(res, 'Bulk');
      alert(bulkResultText(data));
      loadRequests();
    } catch (ex) { err.textContent = ex.message; }
  });
});
document.getElementById('bulkStatusBtn').addEventListener('click', async () => {
  const err = document.getElementById('reqError');
  err.textContent = '';
  try {
    const ids = selectedIds();
    if (!ids.length) return;
    const status = prompt('Новый статус (SUBMITTED/ISSUED/COMPLETED/CANCELLED):');
    if (!status) return;
    await api('/api/requests/bulk', {
      method: 'POST',
      body: JSON.stringify({ ids, operation: 'SET_STATUS', status }),
    }).then(r => jsonOrThrow(r, 'Bulk'));
    loadRequests();
  } catch (ex) { err.textContent = ex.message; }
});
document.getElementById('bulkTagBtn').addEventListener('click', async () => {
  const err = document.getElementById('reqError');
  err.textContent = '';
  try {
    const ids = selectedIds();
    if (!ids.length) return;
    const tag = prompt('Тег:');
    if (!tag) return;
    const op = confirm('OK = добавить, Отмена = убрать') ? 'ADD_TAG' : 'REMOVE_TAG';
    await api('/api/requests/bulk', {
      method: 'POST', body: JSON.stringify({ ids, operation: op, tag }),
    }).then(r => jsonOrThrow(r, 'Bulk'));
    loadRequests();
  } catch (ex) { err.textContent = ex.message; }
});

/* ---------- request card ---------- */
document.getElementById('newReqBtn').addEventListener('click', () => {
  document.getElementById('reqForm').reset();
  document.querySelector('#reqForm [name=id]').value = '';
  document.querySelector('#reqForm [name=version]').value = '';
  document.getElementById('cardTitle').textContent = 'Новая заявка';
  document.getElementById('cardMeta').innerHTML = '';
  document.getElementById('notesList').innerHTML = '';
  state.currentReqId = null;
  show('request-card');
});
document.getElementById('cardBack').addEventListener('click', () => {
  show('requests');
  loadRequests();
});

function renderCardMeta(r) {
  const meta = document.getElementById('cardMeta');
  const parts = [];
  if (r.archived) parts.push('<span class="badge">ARCHIVED</span>');
  if (r.requestCode) parts.push('Код: ' + esc(r.requestCode));
  if (r.passNumber) parts.push('Пропуск: ' + esc(r.passNumber));
  else parts.push('Пропуск: —');
  if (r.arrivedAt) parts.push('Прибыл: ' + esc(r.arrivedAt));
  if (r.leftAt) parts.push('Убыл: ' + esc(r.leftAt));
  meta.innerHTML = parts.join(' · ');
}

async function openCard(id) {
  const err = document.getElementById('cardError');
  err.textContent = '';
  try {
    await refreshEmployeeCache();
    fillEmployeeSelects();
    const res = await api('/api/requests/' + encodeURIComponent(id));
    const r = await jsonOrThrow(res, 'Карточка');
    const f = document.getElementById('reqForm');
    el(f, 'id').value = r.id || id;
    el(f, 'version').value = r.version != null ? r.version : '';
    el(f, 'visitorFullName').value = (r.visitor && r.visitor.fullName) || '';
    el(f, 'visitorDocumentType').value = (r.visitor && r.visitor.documentType) || 'PASSPORT';
    el(f, 'visitorDocumentNumber').value = (r.visitor && r.visitor.documentNumber) || '';
    el(f, 'hostEmployeeId').value = (r.host && r.host.id) || '';
    el(f, 'purpose').value = r.purpose || '';
    el(f, 'visitDate').value = (r.visitDate || '').slice(0, 10);
    el(f, 'timeFrom').value = (r.timeFrom || '').slice(0, 5);
    el(f, 'timeTo').value = (r.timeTo || '').slice(0, 5);
    el(f, 'status').value = r.status || 'SUBMITTED';
    el(f, 'guestCount').value = r.guestCount || 1;
    el(f, 'needsEscort').checked = !!r.needsEscort;
    el(f, 'tags').value = (r.tags || []).join(';');
    document.getElementById('cardTitle').textContent = 'Заявка ' + (r.requestCode || id);
    renderCardMeta(r);
    state.currentReqId = r.id || id;
    await loadNotes(state.currentReqId);
    show('request-card');
  } catch (ex) { err.textContent = ex.message; }
}

document.getElementById('reqForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  const err = document.getElementById('cardError');
  err.textContent = '';
  const payload = {
    visitorFullName: el(f, 'visitorFullName').value,
    visitorDocumentType: el(f, 'visitorDocumentType').value,
    visitorDocumentNumber: el(f, 'visitorDocumentNumber').value,
    hostEmployeeId: el(f, 'hostEmployeeId').value,
    purpose: el(f, 'purpose').value,
    visitDate: el(f, 'visitDate').value,
    timeFrom: el(f, 'timeFrom').value,
    timeTo: el(f, 'timeTo').value,
    status: el(f, 'status').value,
    guestCount: Number(el(f, 'guestCount').value),
    needsEscort: el(f, 'needsEscort').checked,
    tags: el(f, 'tags').value.split(/[;,]/).map(s => s.trim().toLowerCase()).filter(Boolean),
  };
  try {
    let res;
    if (state.currentReqId) {
      payload.version = Number(el(f, 'version').value);
      res = await api('/api/requests/' + encodeURIComponent(state.currentReqId), {
        method: 'PUT', body: JSON.stringify(payload),
      });
    } else {
      res = await api('/api/requests', { method: 'POST', body: JSON.stringify(payload) });
    }
    const saved = await jsonOrThrow(res, 'Сохранение');
    state.currentReqId = saved.id;
    el(f, 'version').value = saved.version != null ? saved.version : '';
    renderCardMeta(saved);
    alert('Сохранено');
  } catch (ex) { err.textContent = ex.message; }
});

document.getElementById('cardArchive').addEventListener('click', async () => {
  const err = document.getElementById('cardError');
  try {
    if (!state.currentReqId) return;
    await api('/api/requests/' + encodeURIComponent(state.currentReqId), { method: 'DELETE' })
      .then(r => jsonOrThrow(r, 'Архивирование'));
    show('requests');
    loadRequests();
  } catch (ex) { err.textContent = ex.message; }
});
document.getElementById('cardRestore').addEventListener('click', async () => {
  const err = document.getElementById('cardError');
  try {
    if (!state.currentReqId) return;
    await api('/api/requests/' + encodeURIComponent(state.currentReqId) + '/restore', { method: 'POST' })
      .then(r => jsonOrThrow(r, 'Восстановление'));
    openCard(state.currentReqId);
  } catch (ex) { err.textContent = ex.message; }
});
document.getElementById('cardDeletePerm').addEventListener('click', async () => {
  const err = document.getElementById('cardError');
  try {
    if (!state.currentReqId) return;
    if (!confirm('Удалить навсегда? Работает только при PERMANENT_DELETE_ENABLED=true.')) return;
    await api('/api/requests/' + encodeURIComponent(state.currentReqId) + '/permanent', { method: 'DELETE' })
      .then(r => jsonOrThrow(r, 'Удаление'));
    show('requests');
    loadRequests();
  } catch (ex) { err.textContent = ex.message; }
});

async function loadNotes(id) {
  const ul = document.getElementById('notesList');
  ul.innerHTML = '';
  const res = await api('/api/requests/' + encodeURIComponent(id) + '/notes');
  const data = await jsonOrThrow(res, 'Заметки');
  const notes = Array.isArray(data) ? data : (data.content || []);
  for (const n of notes) {
    const li = document.createElement('li');
    li.innerHTML = (n.pinned ? '📌 ' : '') + esc(n.text) +
      ' <small>' + esc(n.authorName || '') + ' ' + esc(n.createdAt || '') + '</small> ';
    if (canEdit()) {
      const editB = document.createElement('button');
      editB.textContent = 'Править';
      editB.addEventListener('click', async () => {
        const err = document.getElementById('cardError');
        try {
          const text = prompt('Текст заметки:', n.text);
          if (text == null) return;
          const pinned = confirm('OK = закрепить, Отмена = открепить');
          await api('/api/requests/notes/' + encodeURIComponent(n.id), {
            method: 'PUT', body: JSON.stringify({ text, pinned }),
          }).then(r => jsonOrThrow(r, 'Заметка'));
          loadNotes(id);
        } catch (ex) { err.textContent = ex.message; }
      });
      const del = document.createElement('button');
      del.textContent = 'Удалить';
      del.addEventListener('click', async () => {
        const err = document.getElementById('cardError');
        try {
          await api('/api/requests/notes/' + encodeURIComponent(n.id), { method: 'DELETE' })
            .then(r => jsonOrThrow(r, 'Заметка'));
          loadNotes(id);
        } catch (ex) { err.textContent = ex.message; }
      });
      li.appendChild(editB);
      li.appendChild(del);
    }
    ul.appendChild(li);
  }
}

document.getElementById('noteForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  if (!state.currentReqId) return;
  const err = document.getElementById('cardError');
  err.textContent = '';
  try {
    const fd = new FormData(e.target);
    await api('/api/requests/' + encodeURIComponent(state.currentReqId) + '/notes', {
      method: 'POST',
      body: JSON.stringify({ text: fd.get('text'), pinned: !!fd.get('pinned') }),
    }).then(r => jsonOrThrow(r, 'Заметка'));
    e.target.reset();
    loadNotes(state.currentReqId);
  } catch (ex) { err.textContent = ex.message; }
});

/* ---------- employees ---------- */
async function loadEmployees() {
  const err = document.getElementById('empError');
  err.textContent = '';
  try {
    const activeOnly = document.getElementById('empActiveOnly').checked;
    const res = await api('/api/employees' + (activeOnly ? '?active=true' : ''));
    const rows = await jsonOrThrow(res, 'Сотрудники');
    const tb = document.querySelector('#empTable tbody');
    tb.innerHTML = '';
    for (const m of rows) {
      const tr = document.createElement('tr');
      tr.innerHTML = '<td>' + esc(m.id) + '</td><td>' + esc(m.fullName) + '</td>' +
        '<td>' + esc(m.department) + '</td><td>' + esc(m.role) + '</td>' +
        '<td>' + (m.active ? 'да' : 'нет') + '</td>';
      if (isAdmin()) {
        const td = document.createElement('td');
        const editB = document.createElement('button');
        editB.textContent = 'Править';
        editB.addEventListener('click', () => {
          const f = document.getElementById('empForm');
          el(f, 'id').value = m.id;
          el(f, 'fullName').value = m.fullName || '';
          el(f, 'department').value = m.department || '';
          el(f, 'role').value = m.role || 'OTHER';
        });
        const tgl = document.createElement('button');
        tgl.textContent = m.active ? 'Отключить' : 'Включить';
        tgl.addEventListener('click', async () => {
          const err = document.getElementById('empError');
          try {
            const act = m.active ? 'disable' : 'enable';
            await api('/api/employees/' + encodeURIComponent(m.id) + '/' + act,
              { method: 'POST' }).then(r => jsonOrThrow(r, 'Сотрудник'));
            loadEmployees();
          } catch (ex) { err.textContent = ex.message; }
        });
        td.appendChild(editB);
        td.appendChild(tgl);
        tr.appendChild(td);
      }
      tb.appendChild(tr);
    }
  } catch (ex) { err.textContent = ex.message; }
}
document.getElementById('empActiveOnly').addEventListener('change', loadEmployees);
document.getElementById('empForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  const err = document.getElementById('empError');
  err.textContent = '';
  const payload = {
    fullName: fd.get('fullName'), department: fd.get('department'), role: fd.get('role'),
  };
  try {
    const id = fd.get('id');
    const res = id
      ? await api('/api/employees/' + encodeURIComponent(id), { method: 'PUT', body: JSON.stringify(payload) })
      : await api('/api/employees', { method: 'POST', body: JSON.stringify(payload) });
    await jsonOrThrow(res, 'Сотрудник');
    e.target.reset();
    loadEmployees();
  } catch (ex) { err.textContent = ex.message; }
});

/* ---------- CSV ---------- */
document.getElementById('csvPreviewForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  const info = document.getElementById('csvPreviewInfo');
  info.textContent = '';
  try {
    const res = await api('/api/requests/import/preview', { method: 'POST', body: fd });
    const data = await jsonOrThrow(res, 'Preview');
    info.textContent = 'Валидных строк: ' + data.validCount + ', ошибок: ' + (data.errors || []).length;
    const tb = document.querySelector('#csvErrTable tbody');
    tb.innerHTML = '';
    document.getElementById('csvErrTable').hidden = !(data.errors || []).length;
    for (const er of (data.errors || [])) {
      const tr = document.createElement('tr');
      tr.innerHTML = '<td>' + esc(er.row) + '</td><td>' + esc(er.field) + '</td><td>' + esc(er.message) + '</td>';
      tb.appendChild(tr);
    }
  } catch (ex) { info.textContent = ex.message; }
});
document.getElementById('csvApplyForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  const mode = fd.get('mode');
  const info = document.getElementById('csvApplyInfo');
  info.textContent = '';
  try {
    const body = new FormData();
    body.append('file', fd.get('file'));
    const res = await api('/api/requests/import/apply?mode=' + encodeURIComponent(mode),
      { method: 'POST', body });
    const data = await jsonOrThrow(res, 'Import');
    const errs = (data.errors || []).length;
    info.textContent = 'Создано: ' + data.created + ', обновлено: ' + data.updated +
      ', ошибок: ' + errs;
  } catch (ex) { info.textContent = ex.message; }
});

/* ---------- analytics ---------- */
async function loadAnalytics() {
  const err = document.getElementById('anError');
  err.textContent = '';
  try {
    const res = await api('/api/analytics/summary');
    const a = await jsonOrThrow(res, 'Аналитика');
    const cards = document.getElementById('analyticsCards');
    cards.innerHTML = '';
    for (const [k, v] of Object.entries({
        'Всего': a.total, 'Активных': a.active, 'В архиве': a.archived,
        'С сопровождением': a.escortCount, 'Среднее гостей': a.avgGuestCount,
      })) {
      const d = document.createElement('div');
      d.className = 'card';
      d.innerHTML = '<b>' + esc(v) + '</b><span>' + esc(k) + '</span>';
      cards.appendChild(d);
    }
    const fill = (id, obj) => {
      const tb = document.querySelector('#' + id + ' tbody');
      tb.innerHTML = '';
      for (const [k, v] of Object.entries(obj || {})) {
        const tr = document.createElement('tr');
        tr.innerHTML = '<td>' + esc(k) + '</td><td>' + esc(v) + '</td>';
        tb.appendChild(tr);
      }
    };
    fill('anStatus', a.byStatus);
    fill('anRisk', a.byRisk);
    const et = document.querySelector('#anEmp tbody');
    et.innerHTML = '';
    for (const row of (a.byEmployee || [])) {
      const tr = document.createElement('tr');
      tr.innerHTML = '<td>' + esc(row.employeeName) + ' <small>' + esc(row.employeeId) + '</small></td>' +
        '<td>' + esc(row.count) + '</td>';
      et.appendChild(tr);
    }
  } catch (ex) { err.textContent = ex.message; }
}

/* ---------- users (ADMIN): CRUD + reset-password ---------- */
async function loadUsers() {
  const err = document.getElementById('usersError');
  err.textContent = '';
  try {
    const res = await api('/api/admin/users?page=' + state.usersPage + '&size=20');
    const data = await jsonOrThrow(res, 'Пользователи');
    const list = data.content || [];
    const tb = document.querySelector('#usersTable tbody');
    tb.innerHTML = '';
    for (const u of list) {
      const tr = document.createElement('tr');
      tr.innerHTML = '<td>' + esc(u.id) + '</td><td>' + esc(u.email) + '</td>' +
        '<td>' + esc(u.name) + '</td><td>' + esc((u.roles || []).join(',')) + '</td>' +
        '<td>' + (u.active ? 'да' : 'нет') + '</td>' +
        '<td>' + (u.mustChangePassword ? 'да' : 'нет') + '</td>';
      const td = document.createElement('td');
      const editB = document.createElement('button');
      editB.textContent = 'Править';
      editB.addEventListener('click', () => {
        const f = document.getElementById('userForm');
        el(f, 'id').value = u.id;
        el(f, 'email').value = u.email || '';
        el(f, 'name').value = u.name || '';
        for (const r of ['VIEWER', 'OPERATOR', 'ADMIN']) el(f, r).checked = (u.roles || []).includes(r);
        el(f, 'active').checked = u.active !== false;
      });
      const rst = document.createElement('button');
      rst.textContent = 'Сбросить пароль';
      rst.addEventListener('click', async () => {
        const err = document.getElementById('usersError');
        try {
          const r2 = await api('/api/admin/users/' + encodeURIComponent(u.id) + '/reset-password',
            { method: 'POST' }).then(r => jsonOrThrow(r, 'Сброс пароля'));
          document.getElementById('usersTempPw').textContent =
            'Временный пароль: ' + r2.temporaryPassword;
          loadUsers();
        } catch (ex) { err.textContent = ex.message; }
      });
      td.appendChild(editB);
      td.appendChild(rst);
      tr.appendChild(td);
      tb.appendChild(tr);
    }
    document.getElementById('usersPageInfo').textContent =
      'стр. ' + (state.usersPage + 1) +
      (data.totalPages ? ' из ' + data.totalPages : '');
  } catch (ex) { err.textContent = ex.message; }
}
document.getElementById('usersPrev').addEventListener('click', () => {
  if (state.usersPage > 0) { state.usersPage--; loadUsers(); }
});
document.getElementById('usersNext').addEventListener('click', () => {
  state.usersPage++; loadUsers();
});
document.getElementById('userForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  const err = document.getElementById('usersError');
  err.textContent = '';
  const userRoles = ['VIEWER', 'OPERATOR', 'ADMIN'].filter(r => fd.get(r));
  try {
    const id = fd.get('id');
    if (id) {
      await api('/api/admin/users/' + encodeURIComponent(id), {
        method: 'PUT',
        body: JSON.stringify({
          email: fd.get('email'), name: fd.get('name'),
          roles: userRoles, active: !!fd.get('active'),
        }),
      }).then(r => jsonOrThrow(r, 'Пользователь'));
      document.getElementById('usersTempPw').textContent = '';
    } else {
      const res = await api('/api/admin/users', {
        method: 'POST',
        body: JSON.stringify({ email: fd.get('email'), name: fd.get('name'), roles: userRoles }),
      });
      const data = await jsonOrThrow(res, 'Пользователь');
      document.getElementById('usersTempPw').textContent =
        'Временный пароль: ' + data.temporaryPassword;
    }
    e.target.reset();
    loadUsers();
  } catch (ex) { err.textContent = ex.message; }
});

/* ---------- audit (ADMIN) ---------- */
function currentAuditQuery(page) {
  const fd = new FormData(document.getElementById('auditFilters'));
  const p = new URLSearchParams();
  for (const k of ['q', 'sort', 'dir', 'size']) {
    const v = (fd.get(k) || '').toString().trim();
    if (v) p.set(k, v);
  }
  p.set('page', String(page));
  return p.toString();
}
async function loadAudit() {
  const err = document.getElementById('auditError');
  err.textContent = '';
  try {
    const res = await api('/api/admin/audit?' + currentAuditQuery(state.auditPage));
    const data = await jsonOrThrow(res, 'Аудит');
    const list = data.content || [];
    const tb = document.querySelector('#auditTable tbody');
    tb.innerHTML = '';
    for (const a of list) {
      const tr = document.createElement('tr');
      tr.innerHTML = '<td>' + esc(a.createdAt) + '</td><td>' + esc(a.eventType) + '</td>' +
        '<td>' + esc(a.entityType) + '</td><td>' + esc(a.entityUuid) + '</td>' +
        '<td>' + esc(a.description) + '</td><td>' + esc(a.username) + '</td>' +
        '<td>' + esc(a.correlationId) + '</td>';
      tb.appendChild(tr);
    }
    document.getElementById('auditPageInfo').textContent =
      'стр. ' + (state.auditPage + 1) +
      (data.totalPages ? ' из ' + data.totalPages : '');
  } catch (ex) { err.textContent = ex.message; }
}
document.getElementById('auditFilters').addEventListener('submit', (e) => {
  e.preventDefault();
  state.auditPage = 0;
  loadAudit();
});
document.getElementById('auditPrev').addEventListener('click', () => {
  if (state.auditPage > 0) { state.auditPage--; loadAudit(); }
});
document.getElementById('auditNext').addEventListener('click', () => {
  state.auditPage++; loadAudit();
});

/* ---------- init ---------- */
if (state.user && localStorage.getItem(LS.access)) {
  setLoggedIn(state.user);
  show('requests');
  loadRequests();
} else {
  applyRoleVisibility();
  show('login');
}
