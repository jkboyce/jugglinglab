// Supply browser platform metadata for engine-only tests in Node; no UI is rendered.
globalThis.window ??= { navigator: { language: 'en', userAgent: 'Node engine tests' }, matchMedia: () => ({matches:false}), location: {href:'http://localhost/'} };
if (!globalThis.navigator) Object.defineProperty(globalThis,'navigator',{value:window.navigator});
import {readFileSync} from 'node:fs';
const originalFetch = globalThis.fetch;
globalThis.fetch = async (input, init) => {
 const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
 if(url.startsWith('file:')) return new Response(readFileSync(new URL(url)),{headers:{'Content-Type':url.endsWith('.wasm')?'application/wasm':'application/octet-stream'}});
 return originalFetch(input, init);
};
class FileRequest {
 open(method,url) { this.url=url; }
 send() { queueMicrotask(()=>{try{const data=readFileSync(new URL(this.url));this.response=data.buffer.slice(data.byteOffset,data.byteOffset+data.byteLength);this.status=200;this.onload?.();}catch(e){this.onerror?.(e);}}); }
}
globalThis.XMLHttpRequest = FileRequest;
