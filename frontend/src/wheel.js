// Rows that scroll sideways (product shelves, the department rail, tabs, wide tables) show no scrollbar. A trackpad
// scrolls them with a sideways two-finger swipe on its own; this also turns an up/down swipe or the mouse wheel into
// sideways scrolling while the pointer is over such a row. At either end of the row the page scrolls on as usual,
// so nothing is ever stuck. Touch screens are not affected (they swipe the rows directly).
const SCROLLS = /^(auto|scroll)$/;
const LINE_PX = 40;
const targets = new WeakMap();
const reducedMotion = () => window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;

/** The nearest element under the pointer that scrolls sideways only, or null when the wheel belongs to the page. */
function sidewaysRow(start) {
  for (let el = start; el && el !== document.body && el !== document.documentElement; el = el.parentElement) {
    const style = getComputedStyle(el);
    if (SCROLLS.test(style.overflowY) && el.scrollHeight > el.clientHeight + 1) return null;
    if (SCROLLS.test(style.overflowX) && el.scrollWidth > el.clientWidth + 1) return el;
  }
  return null;
}

export function scrollSidewaysWithWheel() {
  window.addEventListener(
    "wheel",
    (event) => {
      if (event.ctrlKey || event.shiftKey || Math.abs(event.deltaY) <= Math.abs(event.deltaX)) return;
      const row = event.target instanceof Element ? sidewaysRow(event.target) : null;
      if (!row) return;
      const step = event.deltaY * (event.deltaMode === 1 ? LINE_PX : event.deltaMode === 2 ? row.clientWidth : 1);
      const max = row.scrollWidth - row.clientWidth;
      const now = performance.now();
      const pending = targets.get(row);
      const from = pending && now - pending.at < 300 ? pending.left : row.scrollLeft;
      if ((step > 0 && from >= max - 1) || (step < 0 && from <= 1)) return; // the row is at its end: the page scrolls
      event.preventDefault();
      const left = Math.min(max, Math.max(0, from + step));
      targets.set(row, { left, at: now });
      // a mouse wheel moves in big notches, so glide; a trackpad sends many small steps, so follow them directly
      row.scrollTo({ left, behavior: Math.abs(step) >= 50 && !reducedMotion() ? "smooth" : "auto" });
    },
    { passive: false },
  );
}
