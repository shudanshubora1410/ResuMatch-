/**
 * Landing page logic. Three jobs:
 *  1. mount the shared header/footer,
 *  2. run the "live receipt" calculator in the hero (pure arithmetic, no library),
 *  3. prove the chain works by calling GET /api/v1/health and rendering the answer.
 *
 * The hero card is a *demo* of the report format, not the Phase 8 scoring engine - but it uses
 * the real shape: a weight per category, a 0-100 score per category, points = weight x score,
 * and the total is the sum. Every number comes from the data attributes on the rows, so there is
 * exactly one place to change a weight.
 */

import { checkHealth, EVENTS, ApiError } from '../api.js';
import { mountLayout } from '../layout.js';
import { el, pill, statusRow } from '../ui.js';

mountLayout({ active: 'home' });

/* ==========================================================================
   1. The live receipt
   ========================================================================== */

const report = document.getElementById('report');
const ring = document.getElementById('score-ring');
const scoreValue = document.getElementById('score-value');
const scoreTotal = document.getElementById('score-total');
const verdict = document.getElementById('score-verdict');
const note = document.getElementById('score-note');
const rule = document.getElementById('rule-line');
const ruleText = document.getElementById('rule-text');
const foot = document.getElementById('report-foot');
const simSwitch = document.getElementById('sim-docker');

// The summary row has no data-weight, so this filter drops it and keeps the six real rows.
const rows = Array.from(document.querySelectorAll('#mini-rows tr'))
  .filter((row) => row.dataset.weight !== undefined);

const motionOK = !window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** The one rule this demo teaches. Shown to the user, so it is written like the engine would. */
const RULE_TEXT = {
  missing: 'must-have "Docker" missing from the skills section \u2192 Skills scores 80',
  met: '"Docker" found in the skills section \u2192 Skills scores 100',
};

/**
 * Scores the demo resume and writes the per-row points into the table.
 * @param {boolean} withDocker when true the skills row uses data-score-after instead of data-score
 * @returns {number} the weighted total, 0-100
 */
function scoreResume(withDocker) {
  let total = 0;

  rows.forEach((row) => {
    const weight = Number(row.dataset.weight);
    const boosted = withDocker && row.dataset.scoreAfter;
    const score = boosted ? Number(row.dataset.scoreAfter) : Number(row.dataset.score);

    const points = (weight * score) / 100;
    total += points;

    row.querySelector('[data-points]').textContent = points.toFixed(1);
    row.classList.toggle('is-boosted', Boolean(boosted));
  });

  return total;
}

/** Counts a number up to its new value so the change is noticed. */
function tween(node, from, to, decimals) {
  const duration = 420;
  const start = performance.now();

  const step = (now) => {
    const progress = Math.min(1, (now - start) / duration);
    const eased = 1 - ((1 - progress) ** 3);
    node.textContent = (from + (to - from) * eased).toFixed(decimals);
    if (progress < 1) {
      requestAnimationFrame(step);
    }
  };
  requestAnimationFrame(step);
}

/**
 * Walks a CSS custom property from one value to another.
 * A conic-gradient background cannot be transitioned by CSS, so the arc is repainted
 * frame by frame - six rows and one ring is cheap enough for that.
 */
function tweenVar(node, prop, from, to, duration) {
  const start = performance.now();

  const step = (now) => {
    const progress = Math.min(1, (now - start) / duration);
    const eased = 1 - ((1 - progress) ** 3);
    node.style.setProperty(prop, (from + (to - from) * eased).toFixed(2));
    if (progress < 1) {
      requestAnimationFrame(step);
    }
  };
  requestAnimationFrame(step);
}

let lastScore = null;
let lastTotal = null;

/**
 * Repaints the whole card for a given simulation state.
 * @param {boolean} withDocker
 * @param {{animate?: boolean, pulse?: boolean}} options
 */
function renderReport(withDocker, { animate = false, pulse = false } = {}) {
  const total = scoreResume(withDocker);
  const rounded = Math.round(total);

  // One must-have is missing whenever Docker is off, so the state is "partial", not "good".
  const partial = !withDocker;

  report.classList.toggle('is-partial', partial);
  verdict.textContent = partial ? 'Partial match' : (total >= 85 ? 'Strong match' : 'Good match');
  note.textContent = withDocker
    ? 'All 5 must-have skills matched. Rules v1, no model in the loop.'
    : '4 of 5 must-have skills matched. Rules v1, no model in the loop.';
  ruleText.textContent = withDocker ? RULE_TEXT.met : RULE_TEXT.missing;
  rule.classList.toggle('is-met', withDocker);
  // Satisfied rules stop being shown: the card only states what is still true.
  rule.classList.toggle('is-gone', withDocker);
  rule.setAttribute('aria-hidden', String(withDocker));
  foot.textContent = withDocker
    ? 'Skills now scores 100. Nothing else in the report moved.'
    : 'Deterministic: same inputs, same number.';

  ring.setAttribute('aria-label', `Match score: ${rounded} out of 100`);
  if (animate && motionOK) {
    tweenVar(ring, '--pct', lastScore ?? 0, rounded, 520);
  } else {
    ring.style.setProperty('--pct', String(rounded));
  }

  const fromScore = lastScore ?? 0;
  const fromTotal = lastTotal ?? 0;
  lastScore = rounded;
  lastTotal = total;

  if (animate && motionOK) {
    tween(scoreValue, fromScore, rounded, 0);
    tween(scoreTotal, fromTotal, total, 1);
  } else {
    scoreValue.textContent = String(rounded);
    scoreTotal.textContent = total.toFixed(1);
  }

  if (pulse && motionOK) {
    const skillsRow = rows.find((row) => row.dataset.key === 'skills');
    if (skillsRow) {
      skillsRow.classList.remove('is-fired');
      void skillsRow.offsetWidth;
      skillsRow.classList.add('is-fired');
    }
  }
}

/**
 * First paint is a short scoring pass rather than a finished screenshot: the six rows are
 * uncovered top-down, then the ring sweeps to 80 and the total counts up. If motion is not
 * wanted, the card simply appears complete.
 */
function playIntro() {
  if (!motionOK) {
    renderReport(false);
    return;
  }

  report.classList.add('is-booting');
  scoreValue.textContent = '0';
  scoreTotal.textContent = '0.0';
  ring.style.setProperty('--pct', '0');

  const step = 45;
  rows.forEach((row, index) => {
    window.setTimeout(() => row.classList.add('is-resolved'), step * index);
  });

  window.setTimeout(() => {
    renderReport(false, { animate: true });
    report.classList.remove('is-booting');
    // Points that were already in the DOM are re-stated once the rows are visible.
    rows.forEach((row) => row.classList.add('is-resolved'));
  }, step * rows.length + 80);
}

if (rows.length > 0 && simSwitch) {
  playIntro();
  simSwitch.addEventListener('change', () => renderReport(simSwitch.checked, { animate: true, pulse: true }));
}

/* ==========================================================================
   2. Live system status
   ========================================================================== */

const output = document.getElementById('status-output');
const refreshButton = document.getElementById('status-refresh');
const meta = document.getElementById('status-meta');

// Counted per page load, not stored: this is a receipt for this tab's requests only.
let requestsSent = 0;

/** Local wall-clock time, seconds included, so two checks a minute apart are distinguishable. */
function clockTime() {
  return new Date().toLocaleTimeString([], { hour12: false });
}

function setMeta(text) {
  if (meta) {
    meta.textContent = text;
  }
}

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

  const overall = health.status === 'UP' ? pill('UP', 'ok') : pill('DEGRADED', 'warn');
  list.appendChild(statusRow('Overall', overall));
  list.appendChild(statusRow('Version', health.version));
  list.appendChild(statusRow('Environment', health.environment));

  const database = health.database || {};
  const dbPill = database.status === 'UP'
    ? pill(`UP${database.latencyMs !== undefined ? ` ${database.latencyMs} ms` : ''}`, 'ok')
    : pill('DOWN', 'error');
  const dbCell = document.createElement('span');
  dbCell.appendChild(dbPill);
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
  requestsSent += 1;
  const startedAt = clockTime();
  const timer = performance.now();

  refreshButton.disabled = true;
  refreshButton.classList.add('is-busy');
  refreshButton.setAttribute('aria-busy', 'true');
  setMeta(`checking at ${startedAt} · request ${requestsSent}`);
  output.replaceChildren(el('p', 'status-loading', 'Checking the backend…'));

  try {
    const health = await checkHealth();
    renderHealth(health);
    const ms = Math.round(performance.now() - timer);
    setMeta(`checked ${clockTime()} · ${ms} ms · request ${requestsSent}`);
  } catch (error) {
    renderError(error);
    // A failed attempt is still a receipt: say when it failed rather than leaving the old time.
    setMeta(`failed at ${clockTime()} · request ${requestsSent}`);
  } finally {
    refreshButton.disabled = false;
    refreshButton.classList.remove('is-busy');
    refreshButton.removeAttribute('aria-busy');
  }
}

window.addEventListener(EVENTS.WAKING, (event) => showWakingNote(event.detail.attempt));
refreshButton.addEventListener('click', loadHealth);
loadHealth();
