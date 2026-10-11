// jsdom lacks TextEncoder (react-router needs it) and structuredClone; borrow Node's.
const { TextEncoder, TextDecoder } = require('node:util');
if (typeof globalThis.TextEncoder === 'undefined') globalThis.TextEncoder = TextEncoder;
if (typeof globalThis.TextDecoder === 'undefined') globalThis.TextDecoder = TextDecoder;
if (typeof globalThis.structuredClone === 'undefined') {
  globalThis.structuredClone = v => JSON.parse(JSON.stringify(v));
}
// jsdom has no fetch either; msw/node needs the WHATWG classes (the page tests' axios calls go through XHR).
const { ReadableStream, TransformStream, WritableStream } = require('node:stream/web');
const { Blob, File } = require('node:buffer');
const { BroadcastChannel } = require('node:worker_threads');
Object.assign(globalThis, {
  ReadableStream: globalThis.ReadableStream ?? ReadableStream,
  TransformStream: globalThis.TransformStream ?? TransformStream,
  WritableStream: globalThis.WritableStream ?? WritableStream,
  Blob: globalThis.Blob ?? Blob,
  File: globalThis.File ?? File,
  BroadcastChannel: globalThis.BroadcastChannel ?? BroadcastChannel,
});
// undici 6 on purpose: undici 7 opens a MessagePort at load time that keeps the jest worker alive.
const { fetch, Headers, Request, Response, FormData } = require('undici');
Object.assign(globalThis, { fetch, Headers, Request, Response, FormData });
