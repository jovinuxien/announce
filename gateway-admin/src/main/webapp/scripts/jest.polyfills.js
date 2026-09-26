// jsdom lacks TextEncoder (needed by react-router) and structuredClone; borrow Node's.
const { TextEncoder, TextDecoder } = require('node:util');
if (typeof globalThis.TextEncoder === 'undefined') globalThis.TextEncoder = TextEncoder;
if (typeof globalThis.TextDecoder === 'undefined') globalThis.TextDecoder = TextDecoder;
if (typeof globalThis.structuredClone === 'undefined') {
  globalThis.structuredClone = (v) => JSON.parse(JSON.stringify(v));
}
