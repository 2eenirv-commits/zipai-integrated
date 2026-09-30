(function () {
  'use strict';

  const loaderScript = document.currentScript;
  const staticRoot = loaderScript
    ? new URL('../', loaderScript.src)
    : new URL('../../static/', window.location.href);

  function ensureStyle(fileName) {
    const href = new URL('css/' + fileName, staticRoot).href;
    const existing = Array.from(document.querySelectorAll('link[rel="stylesheet"]')).find(function (link) {
      return new URL(link.href, window.location.href).pathname === new URL(href).pathname;
    });
    if (existing) {
      if (existing.sheet) return Promise.resolve();
      return new Promise(function (resolve) {
        existing.addEventListener('load', resolve, { once: true });
        existing.addEventListener('error', resolve, { once: true });
      });
    }

    return new Promise(function (resolve) {
      const link = document.createElement('link');
      link.rel = 'stylesheet';
      link.href = href;
      link.addEventListener('load', resolve, { once: true });
      link.addEventListener('error', resolve, { once: true });
      document.head.appendChild(link);
    });
  }

  function ensureScript(fileName) {
    const src = new URL('js/' + fileName, staticRoot).href;
    const existing = Array.from(document.scripts).find(function (script) {
      if (!script.src) return false;
      return new URL(script.src, window.location.href).pathname === new URL(src).pathname;
    });
    if (existing) return Promise.resolve();

    return new Promise(function (resolve) {
      const script = document.createElement('script');
      script.src = src;
      script.defer = true;
      script.addEventListener('load', resolve, { once: true });
      script.addEventListener('error', resolve, { once: true });
      document.body.appendChild(script);
    });
  }

  function componentFromHtml(html, selector) {
    const documentFragment = new DOMParser().parseFromString(html, 'text/html');
    return documentFragment.querySelector(selector);
  }

  function normalizePath(pathname) {
    const path = String(pathname || '/').replace(/\\/g, '/').replace(/\/+$/, '');
    return path || '/';
  }

  function menuGroupForPath(pathname) {
    const path = normalizePath(pathname).toLowerCase();
    if (path === '/' || path === '/index.html' || path.startsWith('/properties/')) return 'property';
    if (path.startsWith('/defense/')) return 'defense';
    if (path.startsWith('/safe/')) return 'safe';
    if (path === '/board/finance-policy' || /^\/board\/trend[1-4]$/.test(path)) return 'finance';
    if (path === '/board/happy-housing') return 'happy';
    if (path === '/ai/lifestyle-analysis' || path === '/ai/lifestyle-analysis.html') return 'lifestyle';
    if (path.startsWith('/board/community')) return 'community';
    return '';
  }

  function setupHeader(header) {
    const menuButton = header.querySelector('.mobile-menu-button, .menu-toggle');
    const headerMenu = header.querySelector('.header-menu');
    const menuItems = Array.from(header.querySelectorAll('.menu-item'));
    const megaPanel = header.querySelector('.mega-menu-panel');
    const megaGroups = Array.from(header.querySelectorAll('.mega-group'));
    const desktopMedia = window.matchMedia('(min-width: 921px)');
    let closeTimer = null;

    function groupFor(name) {
      return megaGroups.find(function (group) {
        return group.dataset.megaGroup === name;
      });
    }

    function setFocusedGroup(name) {
      megaGroups.forEach(function (group) {
        group.hidden = false;
        group.classList.toggle('is-focus', group.dataset.megaGroup === name);
      });
    }

    function openMega(name) {
      if (!desktopMedia.matches || !megaPanel) return;
      clearTimeout(closeTimer);
      setFocusedGroup(name);
      megaPanel.hidden = false;
      header.classList.add('mega-open');
      megaPanel.setAttribute('aria-hidden', 'false');
    }

    function closeMega(delay) {
      if (!megaPanel) return;
      clearTimeout(closeTimer);
      closeTimer = window.setTimeout(function () {
        header.classList.remove('mega-open');
        megaPanel.setAttribute('aria-hidden', 'true');
        megaPanel.hidden = true;
        megaGroups.forEach(function (group) {
          group.hidden = true;
          group.classList.remove('is-focus');
        });
      }, delay || 0);
    }

    function setMobileItemOpen(item, isOpen) {
      if (!item) return;
      const toggle = item.querySelector('.submenu-toggle');
      const mobileSubmenu = item.querySelector('.mobile-submenu');
      item.classList.toggle('is-open', isOpen);
      if (mobileSubmenu) mobileSubmenu.hidden = !isOpen;
      if (toggle) {
        toggle.setAttribute('aria-expanded', String(isOpen));
        const baseLabel = toggle.dataset.baseLabel || toggle.getAttribute('aria-label') || '';
        if (!toggle.dataset.baseLabel) toggle.dataset.baseLabel = baseLabel;
        toggle.setAttribute('aria-label', baseLabel.replace('열기', isOpen ? '닫기' : '열기'));
      }
    }

    function closeMobileSubmenus(exceptItem) {
      menuItems.forEach(function (item) {
        if (item !== exceptItem) setMobileItemOpen(item, false);
      });
    }

    menuItems.forEach(function (item) {
      const groupName = item.dataset.menuGroup;
      const sourceGroup = groupFor(groupName);
      if (sourceGroup && !item.querySelector('.mobile-submenu')) {
        const mobileSubmenu = document.createElement('div');
        mobileSubmenu.className = 'mobile-submenu';
        mobileSubmenu.hidden = true;
        sourceGroup.querySelectorAll(':scope > a').forEach(function (sourceLink) {
          mobileSubmenu.appendChild(sourceLink.cloneNode(true));
        });
        item.appendChild(mobileSubmenu);
      }
    });

    if (menuButton && headerMenu) {
      function setMenuOpen(isOpen) {
        headerMenu.classList.toggle('is-open', isOpen);
        menuButton.setAttribute('aria-expanded', String(isOpen));
        menuButton.setAttribute('aria-label', isOpen ? '전체 메뉴 닫기' : '전체 메뉴 열기');
        const icon = menuButton.querySelector('i');
        if (icon) icon.className = isOpen ? 'fa-solid fa-xmark' : 'fa-solid fa-bars';
        if (!isOpen) closeMobileSubmenus();
      }

      menuButton.addEventListener('click', function () {
        setMenuOpen(!headerMenu.classList.contains('is-open'));
      });

      document.addEventListener('keydown', function (event) {
        if (event.key !== 'Escape') return;
        closeMega(0);
        closeMobileSubmenus();
        if (headerMenu.classList.contains('is-open')) setMenuOpen(false);
      });

      document.addEventListener('click', function (event) {
        if (!header.contains(event.target)) {
          closeMega(0);
          closeMobileSubmenus();
          if (headerMenu.classList.contains('is-open')) setMenuOpen(false);
        }
      });
    }

    menuItems.forEach(function (item) {
      const groupName = item.dataset.menuGroup;
      const sourceGroup = groupFor(groupName);
      const toggle = item.querySelector('.submenu-toggle');

      if (toggle) {
        toggle.addEventListener('click', function (event) {
          event.preventDefault();
          event.stopPropagation();

          if (desktopMedia.matches) {
            if (header.classList.contains('mega-open') && item.classList.contains('is-open')) {
              item.classList.remove('is-open');
              closeMega(0);
              return;
            }
            menuItems.forEach(function (other) { other.classList.remove('is-open'); });
            item.classList.add('is-open');
            openMega(groupName);
            return;
          }

          const nextOpen = !item.classList.contains('is-open');
          closeMobileSubmenus(item);
          setMobileItemOpen(item, nextOpen);
        });
      }

      item.addEventListener('mouseenter', function () {
        if (!desktopMedia.matches || !sourceGroup) return;
        menuItems.forEach(function (other) { other.classList.toggle('is-open', other === item); });
        openMega(groupName);
      });

      item.addEventListener('focusin', function () {
        if (!desktopMedia.matches || !sourceGroup) return;
        menuItems.forEach(function (other) { other.classList.toggle('is-open', other === item); });
        openMega(groupName);
      });
    });

    if (megaPanel) {
      megaPanel.addEventListener('mouseenter', function () {
        if (desktopMedia.matches) clearTimeout(closeTimer);
      });
      megaPanel.addEventListener('mouseleave', function () {
        if (desktopMedia.matches) {
          menuItems.forEach(function (item) { item.classList.remove('is-open'); });
          closeMega(110);
        }
      });
    }

    const menuArea = header.querySelector('.menu-links');
    if (menuArea) {
      menuArea.addEventListener('mouseleave', function () {
        if (!desktopMedia.matches) return;
        closeMega(180);
      });
    }


    desktopMedia.addEventListener('change', function () {
      closeMega(0);
      closeMobileSubmenus();
      if (headerMenu) headerMenu.classList.remove('is-open');
      if (menuButton) {
        menuButton.setAttribute('aria-expanded', 'false');
        menuButton.setAttribute('aria-label', '전체 메뉴 열기');
        const icon = menuButton.querySelector('i');
        if (icon) icon.className = 'fa-solid fa-bars';
      }
    });

    const activeGroup = menuGroupForPath(window.location.pathname);
    menuItems.forEach(function (item) {
      const primary = item.querySelector('.menu-primary');
      const active = item.dataset.menuGroup === activeGroup;
      item.classList.toggle('active', active);
      if (primary) {
        primary.classList.toggle('active', active);
        if (active) primary.setAttribute('aria-current', 'page');
        else primary.removeAttribute('aria-current');
      }
    });

    const currentPath = normalizePath(window.location.pathname).toLowerCase();
    const allSubLinks = header.querySelectorAll('.mega-group > a, .mobile-submenu a');
    allSubLinks.forEach(function (link) {
      const target = new URL(link.href, window.location.origin);
      const exactPath = normalizePath(target.pathname).toLowerCase() === currentPath;
      const exactHash = !target.hash || target.hash === window.location.hash;
      link.classList.toggle('active', exactPath && exactHash);
    });
  }

  function setupFooter() {
    // Compact footer intentionally has no interactive common control.
  }

  function loadCommonLayout() {
    const headerTarget = document.querySelector('.zipai-header');
    const footerTarget = document.querySelector('.zipai-footer');
    if (!document.documentElement.id) document.documentElement.id = 'pageTop';

    const stylesReady = Promise.all([
      ensureStyle('common.css'),
      ensureStyle('header.css'),
      ensureStyle('footer.css'),
      ensureStyle('chatbot.css')
    ]);

    const headerRequest = headerTarget
      ? Promise.all([stylesReady, fetch('/common/header.html', { cache: 'no-store' })]).then(function (results) {
          const response = results[1];
          if (!response.ok) throw new Error('공통 헤더를 불러오지 못했습니다.');
          return response.text();
        }).then(function (html) {
          const header = componentFromHtml(html, '.zipai-header');
          if (!header) return;
          headerTarget.replaceWith(header);
          setupHeader(header);
        })
      : Promise.resolve();

    const footerRequest = footerTarget
      ? Promise.all([stylesReady, fetch('/common/footer.html', { cache: 'no-store' })]).then(function (results) {
          const response = results[1];
          if (!response.ok) throw new Error('공통 푸터를 불러오지 못했습니다.');
          return response.text();
        }).then(function (html) {
          const footer = componentFromHtml(html, '.zipai-footer');
          if (!footer) return;
          footerTarget.replaceWith(footer);
          setupFooter(footer);
        })
      : Promise.resolve();

    Promise.all([headerRequest, footerRequest]).then(function () {
      if (window.ZipaiAuth) {
        if (typeof window.ZipaiAuth.refreshHeaderUi === 'function') window.ZipaiAuth.refreshHeaderUi();
        else window.ZipaiAuth.updateLoginButtons();
      }
      return ensureScript('chatbot.js');
    }).catch(function (error) {
      console.warn(error.message);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', loadCommonLayout);
  } else {
    loadCommonLayout();
  }
})();
