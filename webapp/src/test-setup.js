import '@testing-library/jest-dom'

if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

if (typeof globalThis.EventSource === 'undefined') {
  globalThis.EventSource = class {
    constructor() {}
    addEventListener() {}
    close() {}
  }
}
