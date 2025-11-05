import 'whatwg-fetch';
import { vi } from 'vitest';

global.EventSource = vi.fn(() => ({
  onmessage: vi.fn(),
  onerror: vi.fn(),
  close: vi.fn(),
}));