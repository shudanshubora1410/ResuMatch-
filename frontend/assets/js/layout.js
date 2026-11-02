/**
 * Shared header and footer, injected into every page.
 *
 * Why injected instead of copied: navigation, theme toggle and the copyright line exist once, so
 * a change (for example adding a "Sign in" link in Phase 1) cannot drift between pages.
 *
 * Usage in a page:  <header id="site-header"></header> ... <footer id="site-footer"></footer>
 * then call mountLayout({ active: 'home' }) from the page module.
 */

import { showToast } from './ui.js';

const THEME_KEY = 'resumatch-theme';

const NAV_ITEMS = [
  { id: 'home', label: 'Home', href: 'index.html' },
  { id: 'how', label: 'How it works', href: 'index.html#how' },
  { id: 'features', label: 'Features', href: 'index.html#features' },
  { id: 'status', label: 'Status', href: 'index.html#status' },
];

const SUPPORT_EMAIL = 'support.resumatch@gmail.com';

function brandMark(text) {
  const mark = document.createElement('span');
  mark.className = 'brand__mark';
  mark.setAttribute('aria-hidden', 'true');
  mark.textContent = text;
  return mark;
}

function buildHeader(active) {
  const inner = document.createElement('div');
  inner.className = 'container site-header__inner';

  const brand = document.createElement('a');
  brand.className = 'brand';
  brand.href = 'index.html';
  brand.appendChild(brandMark('RM'));
  const brandText = document.createElement('span');
  brandText.textContent = 'ResuMatch';
  brand.appendChild(brandText);
  inner.appendChild(brand);

  const nav = document.createElement('nav');
  nav.className = 'nav';
  nav.setAttribute('aria-label', 'Main navigation');
  NAV_ITEMS.forEach((item) => {
    const link = document.createElement('a');
    link.href = item.href;
    link.textContent = item.label;
    if (item.id === active) {
      link.setAttribute('aria-current', 'page');
    }
    nav.appendChild(link);
  });
  inner.appendChild(nav);

  const actions = document.createElement('div');
  actions.className = 'header__actions';

  // Sign-in and registration are Phase 1 features; the buttons say so instead of pretending.
  const signIn = document.createElement('button');
  signIn.type = 'button';
  signIn.className = 'btn btn--small btn--ghost';
  signIn.textContent = 'Sign in';
  signIn.addEventListener('click', () => {
    showToast('Accounts arrive in Phase 1 - the API is already live.', 'info');
  });

  const getStarted = document.createElement('button');
  getStarted.type = 'button';
  getStarted.className = 'btn btn--small btn--primary';
  getStarted.textContent = 'Get started';
  getStarted.addEventListener('click', () => {
    showToast('Registration arrives in Phase 1. Meanwhile, check the live API status.', 'info');
  });

  const themeToggle = document.createElement('button');
  themeToggle.type = 'button';
  themeToggle.className = 'theme-toggle';
  themeToggle.id = 'theme-toggle';
  themeToggle.setAttribute('aria-label', 'Switch colour theme');
  themeToggle.addEventListener('click', toggleTheme);

  actions.append(signIn, getStarted, themeToggle);
  inner.appendChild(actions);
  return inner;
}

function buildFooter() {
  const grid = document.createElement('div');
  grid.className = 'container footer__grid';

  const brand = document.createElement('div');
  brand.className = 'footer__brand';
  const title = document.createElement('div');
  title.className = 'brand';
  title.appendChild(brandMark('RM'));
  const titleText = document.createElement('span');
  titleText.textContent = 'ResuMatch';
  title.appendChild(titleText);
  brand.appendChild(title);

  const tagline = document.createElement('p');
  tagline.textContent = 'Explainable resume screening: deterministic scores you can audit, '
    + 'AI that only explains.';
  brand.appendChild(tagline);

  const contact = document.createElement('p');
  const supportLink = document.createElement('a');
  supportLink.href = `mailto:${SUPPORT_EMAIL}`;
  supportLink.textContent = SUPPORT_EMAIL;
  contact.append('Support: ', supportLink);
  brand.appendChild(contact);
  grid.appendChild(brand);

  const links = document.createElement('ul');
  links.className = 'footer__links';
  const items = [
    { label: 'How it works', href: 'index.html#how' },
    { label: 'Features', href: 'index.html#features' },
    { label: 'API status', href: 'index.html#status' },
    { label: 'GitHub', href: 'https://github.com/shudanshubora1410', external: true },
    { label: 'LinkedIn', href: 'https://linkedin.com/in/shudanshubora', external: true },
  ];
  items.forEach((item) => {
    const li = document.createElement('li');
    const link = document.createElement('a');
    link.href = item.href;
    link.textContent = item.label;
    if (item.external) {
      link.target = '_blank';
      // noreferrer prevents the new tab from reading our page's URL via window.opener.
      link.rel = 'noopener noreferrer';
    }
    li.appendChild(link);
    links.appendChild(li);
  });
  grid.appendChild(links);

  const bottom = document.createElement('div');
  bottom.className = 'container footer__bottom';
  const copyright = document.createElement('span');
  copyright.textContent = '\u00A9 2026 ResuMatch. Built by Shudanshu Bora';
  const support = document.createElement('span');
  support.textContent = 'Made for candidates and recruiters who want reasons, not black boxes.';
  bottom.append(copyright, support);

  // The two children already carry their own .container, so the wrapper needs no class.
  const wrapper = document.createElement('div');
  wrapper.append(grid, bottom);
  return wrapper;
}

/* ---------- Theme ---------- */

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
    toggle.textContent = theme === 'dark' ? '☀' : '☾';
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
}
