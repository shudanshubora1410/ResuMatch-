/**
 * Landing page logic: it does one useful thing - it proves the whole chain works
 * (browser -> Vercel/Tomcat -> Java servlet -> HikariCP -> MySQL) by calling the health endpoint.
 */

import { checkHealth, EVENTS, ApiError } from '../api.js';
import { mountLayout } from '../layout.js';
import { el, pill, statusRow } from '../ui.js';

const output = document.getElementById('status-output');
const refreshButton = document.getElementById('status-refresh');

mountLayout({ active: 'home' });

/** Shows the "server is waking up" note while api.js retries. */
function showWakingNote(maxAttempts) {
  output.replaceChildren();
  const box = el('p', 'waking');
  box.appendChild(el('span', 'spinner'));
  box.appendChild(el('span', null,
    `Waking up the server… the free tier sleeps when idle, so the first request can take up to a minute (attempt ${maxAttempts}).`));
  output.appendChild(box);
}

function renderHealth(health) {
  output.replaceChildren();

  const list = document.createElement('dl');
  list.style.margin = '0';

  const statusPill = health.status === 'UP'
    ? pill('UP', 'ok')
    : pill('DEGRADED', 'warn');

  list.appendChild(statusRow('Overall', statusPill));
  list.appendChild(statusRow('Version', health.version));
  list.appendChild(statusRow('Environment', health.environment));

  const database = health.database || {};
  const dbPill = database.status === 'UP'
    ? pill(`UP${database.latencyMs !== undefined ? ` (${database.latencyMs} ms)` : ''}`, 'ok')
    : pill('DOWN', 'error');
  const dbCell = document.createElement('span');
  dbCell.appendChild(dbPill);
  if (database.detail) {
    const detail = el('span', null, ` ${database.detail}`);
    detail.style.fontWeight = '400';
    detail.style.color = 'var(--text-muted)';
    dbCell.appendChild(detail);
  }
  list.appendChild(statusRow('Database', dbCell));

  const migrations = health.migrations || {};
  const migrationPill = migrations.status === 'SUCCEEDED'
    ? pill('SUCCEEDED', 'ok')
    : pill(migrations.status || 'UNKNOWN', 'warn');
  list.appendChild(statusRow('Migrations', migrationPill));
  list.appendChild(statusRow('File storage', health.storage || '-'));
  list.appendChild(statusRow('Email', health.mailProvider || '-'));
  list.appendChild(statusRow('Uptime', `${health.uptimeSeconds}s`));

  output.appendChild(list);
}

function renderError(error) {
  output.replaceChildren();
  const message = error instanceof ApiError
    ? error.message
    : 'Unexpected error while contacting the API.';
  output.appendChild(el('p', 'waking', message));
  output.appendChild(el('p', 'status-loading',
    'If you are on the free hosting tier, wait a few seconds and press "Check again".'));
}

async function loadHealth() {
  refreshButton.disabled = true;
  output.replaceChildren(el('p', 'status-loading', 'Checking the backend…'));

  try {
    const health = await checkHealth();
    renderHealth(health);
  } catch (error) {
    renderError(error);
  } finally {
    refreshButton.disabled = false;
  }
}

// api.js fires this while it retries a cold start, so the UI can explain the wait.
window.addEventListener(EVENTS.WAKING, (event) => showWakingNote(event.detail.attempt));

refreshButton.addEventListener('click', loadHealth);

loadHealth();
