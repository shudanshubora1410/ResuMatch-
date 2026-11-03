/**
 * Shared header and footer, injected into every page.
 *
 * Why injected instead of copied into each page: navigation, the theme toggle and the copyright
 * line exist once, so adding a "Sign in" link in Phase 1 cannot drift between pages.
 *
 * Usage in a page:  <header id="site-header"></header> ... <footer id="site-footer"></footer>
 * then call mountLayout({ active: 'home' }) from the page module.
 */

import { showToast } from './ui.js';

const THEME_KEY = 'resumatch-theme';
const SUPPORT_EMAIL = 'support.resumatch@gmail.com';
const GITHUB_URL = 'https://github.com/shudanshubora1410';
const LINKEDIN_URL = 'https://linkedin.com/in/shudanshubora';

// No "Home" item: the brand mark already returns to the top of the page.
const NAV_ITEMS = [
  { id: 'how', label: 'How it works', href: 'index.html#how' },
  { id: 'whos-it-for', label: "Who it's for", href: 'index.html#whos-it-for' },
  { id: 'status', label: 'Infrastructure', href: 'index.html#status' },
];

const FOOTER_LINKS = [
  { label: 'How it works', href: 'index.html#how' },
  { label: "Who it's for", href: 'index.html#whos-it-for' },
  { label: 'Infrastructure', href: 'index.html#status' },
  { label: 'GitHub', href: GITHUB_URL, external: true },
  { label: 'LinkedIn', href: LINKEDIN_URL, external: true },
  { label: 'Support', href: `mailto:${SUPPORT_EMAIL}` },
];

const SVG_NS = 'http://www.w3.org/2000/svg';

function node(tag, className, text) {
  const element = document.createElement(tag);
  if (className) {
    element.className = className;
  }
  if (text !== undefined) {
    element.textContent = text;
  }
  return element;
}

/** Builds a tiny stroke icon from path data. No icon library, no icon font. */
function icon(paths, circles = []) {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('width', '16');
  svg.setAttribute('height', '16');
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '1.8');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('aria-hidden', 'true');
  paths.forEach((d) => {
    const path = document.createElementNS(SVG_NS, 'path');
    path.setAttribute('d', d);
    svg.appendChild(path);
  });
  circles.forEach((attrs) => {
    const circle = document.createElementNS(SVG_NS, 'circle');
    Object.entries(attrs).forEach(([key, value]) => circle.setAttribute(key, value));
    svg.appendChild(circle);
  });
  return svg;
}

const MOON_ICON = () => icon(['M20.5 14.6A8.6 8.6 0 0 1 9.4 3.5a8.6 8.6 0 1 0 11.1 11.1Z']);
const SUN_ICON = () => icon(
  ['M12 3v1.6', 'M12 19.4V21', 'M3 12h1.6', 'M19.4 12H21', 'M5.6 5.6l1.1 1.1',
   'M17.3 17.3l1.1 1.1', 'M18.4 5.6l-1.1 1.1', 'M6.7 17.3l-1.1 1.1'],
  [{ cx: '12', cy: '12', r: '3.4' }],
);

function brandBlock() {
  const brand = node('a', 'brand');
  brand.href = 'index.html';
  const mark = node('span', 'brand__mark', 'R');
  mark.setAttribute('aria-hidden', 'true');
  brand.append(mark, node('span', null, 'ResuMatch'));
  return brand;
}

/* --------------------------------------------------------------------------
   Header
   -------------------------------------------------------------------------- */

function buildHeader(active) {
  const inner = node('div', 'container header__inner');
  inner.appendChild(brandBlock());

  const nav = node('nav', 'nav');
  nav.id = 'site-nav';
  nav.setAttribute('aria-label', 'Main navigation');
  const list = node('ul');
  NAV_ITEMS.forEach((item) => {
    const li = node('li');
    const link = node('a', null, item.label);
    link.href = item.href;
    if (item.id === active) {
      link.setAttribute('aria-current', 'page');
    }
    li.appendChild(link);
    list.appendChild(li);
  });
  nav.appendChild(list);
  inner.appendChild(nav);

  const actions = node('div', 'header__actions');

  const themeToggle = node('button', 'theme-toggle');
  themeToggle.type = 'button';
  themeToggle.id = 'theme-toggle';
  themeToggle.setAttribute('aria-label', 'Switch colour theme');
  themeToggle.appendChild(MOON_ICON());
  themeToggle.addEventListener('click', toggleTheme);

  const cta = node('a', 'btn btn--primary btn--sm', 'See a live score');
  cta.href = '#report';
  // On the landing page the CTA should land the reader *on the control*, not just near it.
  // When the demo card is absent (other pages), the plain anchor already does the right thing.
  cta.addEventListener('click', (event) => {
    const report = document.getElementById('report');
    const simSwitch = document.getElementById('sim-docker');
    if (!report || !simSwitch) {
      return;
    }
    event.preventDefault();
    report.scrollIntoView({ behavior: 'smooth', block: 'center' });
    window.setTimeout(() => {
      simSwitch.focus({ preventScroll: true });
      showToast('Toggle Docker to watch the score move.', 'info');
    }, 420);
  });

  const navToggle = node('button', 'nav-toggle');
  navToggle.type = 'button';
  navToggle.setAttribute('aria-expanded', 'false');
  navToggle.setAttribute('aria-controls', 'site-nav');
  navToggle.setAttribute('aria-label', 'Open menu');
  navToggle.append(node('span'), node('span'), node('span'));
  navToggle.addEventListener('click', () => {
    const header = document.getElementById('site-header');
    setMenuOpen(!header.classList.contains('is-open'));
  });

  actions.append(themeToggle, cta, navToggle);
  inner.appendChild(actions);
  return inner;
}

function setMenuOpen(open) {
  const header = document.getElementById('site-header');
  if (!header) {
    return;
  }
  header.classList.toggle('is-open', open);
  const toggle = header.querySelector('.nav-toggle');
  if (toggle) {
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
  }
}

/* --------------------------------------------------------------------------
   Footer - deliberately one quiet row, not a link farm
   -------------------------------------------------------------------------- */

function buildFooter() {
  const row = node('div', 'container footer__row');
  row.appendChild(brandBlock());

  const links = node('ul', 'footer__links');
  FOOTER_LINKS.forEach((item) => {
    const li = node('li');
    const link = node('a', null, item.label);
    link.href = item.href;
    if (item.external) {
      link.target = '_blank';
      // noreferrer stops the new tab from reading our page URL through window.opener.
      link.rel = 'noopener noreferrer';
    }
    li.appendChild(link);
    links.appendChild(li);
  });
  row.appendChild(links);

  const bottom = node('div', 'container footer__bottom');
  bottom.appendChild(node('span', null, '\u00A9 2026 ResuMatch. Built by Shudanshu Bora'));
  bottom.appendChild(node('span', null, 'Reasons, not black boxes.'));

  const wrapper = node('div');
  wrapper.append(row, bottom);
  return wrapper;
}

/* --------------------------------------------------------------------------
   Theme
   -------------------------------------------------------------------------- */

function preferredTheme() {
  const stored = localStorage.getItem(THEME_KEY);
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  const toggle = document.getElementById('theme-toggle');
  if (toggle) {
    const next = theme === 'dark' ? 'light' : 'dark';
    // Show the mode you would switch TO: a moon while light, a sun while dark.
    toggle.replaceChildren(theme === 'dark' ? SUN_ICON() : MOON_ICON());
    toggle.setAttribute('aria-label', `Switch to ${next} mode`);
    toggle.setAttribute('aria-pressed', String(theme === 'dark'));
  }
}

function toggleTheme() {
  const current = document.documentElement.getAttribute('data-theme') || 'light';
  const next = current === 'dark' ? 'light' : 'dark';
  localStorage.setItem(THEME_KEY, next);
  applyTheme(next);
}

/* --------------------------------------------------------------------------
   Mount
   -------------------------------------------------------------------------- */

/**
 * Renders the shared chrome. Call once per page.
 * @param {{active?: string}} options active = id of the current nav item
 */
export function mountLayout({ active } = {}) {
  const header = document.getElementById('site-header');
  const footer = document.getElementById('site-footer');

  if (header) {
    header.replaceChildren(buildHeader(active));
  }
  if (footer) {
    footer.replaceChildren(buildFooter());
  }

  applyTheme(preferredTheme());

  // Follow the OS setting until the user makes an explicit choice.
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (event) => {
    if (!localStorage.getItem(THEME_KEY)) {
      applyTheme(event.matches ? 'dark' : 'light');
    }
  });

  const onScroll = () => {
    if (header) {
      header.classList.toggle('is-stuck', window.scrollY > 8);
    }
  };
  onScroll();
  window.addEventListener('scroll', onScroll, { passive: true });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      setMenuOpen(false);
    }
  });
  document.getElementById('site-header')?.addEventListener('click', (event) => {
    if (event.target.closest('.nav a')) {
      setMenuOpen(false);
    }
  });
}
