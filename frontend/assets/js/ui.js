/**
 * Tiny UI helpers shared by all pages (toasts and safe escaping).
 *
 * Rule of thumb enforced here: user or API supplied text is only ever inserted with
 * textContent, never with innerHTML, which is what keeps the pages free of XSS holes.
 */

const TOAST_TIMEOUT_MS = 4500;

export function showToast(message, type = 'info') {
  const region = document.getElementById('toast-region');
  if (!region) {
    return;
  }
  const toast = document.createElement('div');
  toast.className = `toast toast--${type}`;
  toast.textContent = message;
  region.appendChild(toast);
  setTimeout(() => toast.remove(), TOAST_TIMEOUT_MS);
}

/** Creates an element with an optional class list and text content (never HTML). */
export function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) {
    node.className = className;
  }
  if (text !== undefined && text !== null) {
    node.textContent = String(text);
  }
  return node;
}

/** A definition-list style row used by the status card. */
export function statusRow(label, value) {
  const row = document.createElement('div');
  row.className = 'status-row';
  row.appendChild(el('dt', null, label));

  const dd = document.createElement('dd');
  if (value instanceof Node) {
    dd.appendChild(value);
  } else {
    dd.textContent = value === undefined || value === null ? '-' : String(value);
  }
  row.appendChild(dd);
  return row;
}

/** Builds a coloured status pill. */
export function pill(text, variant) {
  return el('span', `pill pill--${variant}`, text);
}

/** Human friendly "2 minutes ago" for timestamps. */
export function relativeTime(isoString) {
  if (!isoString) {
    return '-';
  }
  const then = new Date(isoString).getTime();
  if (Number.isNaN(then)) {
    return '-';
  }
  const seconds = Math.max(0, Math.round((Date.now() - then) / 1000));
  if (seconds < 60) {
    return `${seconds}s ago`;
  }
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) {
    return `${minutes}m ago`;
  }
  return `${Math.round(minutes / 60)}h ago`;
}
