// Zero-dependency dev server: serves this folder and proxies /api/* to the backend.
// Usage: node dev-server.mjs            (PORT=5173, API_TARGET=http://localhost:8080)
import fs from 'node:fs';
import http from 'node:http';
import https from 'node:https';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const port = Number(process.env.PORT || 5173);
const target = new URL(process.env.API_TARGET || 'http://localhost:8080');
const transport = target.protocol === 'https:' ? https : http;

const types = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8', '.svg': 'image/svg+xml', '.ico': 'image/x-icon',
  '.png': 'image/png', '.txt': 'text/plain; charset=utf-8',
};

function proxy(req, res) {
  const upstream = transport.request({
    protocol: target.protocol,
    hostname: target.hostname,
    port: target.port || (target.protocol === 'https:' ? 443 : 80),
    path: req.url,
    method: req.method,
    headers: { ...req.headers, host: target.host },
  }, (upstreamRes) => {
    res.writeHead(upstreamRes.statusCode, upstreamRes.headers);
    upstreamRes.pipe(res);
  });
  upstream.on('error', () => {
    res.writeHead(502, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 502, error: 'BAD_GATEWAY', message: `Backend not reachable at ${target.origin}` }));
  });
  req.pipe(upstream);
}

function serveStatic(req, res) {
  const urlPath = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
  const relative = urlPath === '/' ? 'index.html' : urlPath.replace(/^\/+/, '');
  const file = path.resolve(root, relative);
  const hidden = relative.split('/').some((part) => part.startsWith('.'));
  if (!file.startsWith(root + path.sep) || hidden || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Not found');
    return;
  }
  res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
  fs.createReadStream(file).pipe(res);
}

http.createServer((req, res) => {
  if (req.url === '/api' || req.url.startsWith('/api/')) proxy(req, res);
  else serveStatic(req, res);
}).listen(port, () => {
  console.log(`Frontend:  http://localhost:${port}`);
  console.log(`Proxying   /api/*  ->  ${target.origin}`);
});
