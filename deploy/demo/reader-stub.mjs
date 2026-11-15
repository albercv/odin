// Lector de prueba para `make demo`: no lee nada, devuelve siempre el mismo texto ficticio.
// Salvo las fotos de la demo, que el lector no recibe con su nombre: las reconoce por una marca
// en el propio PNG («demo-sin-texto»: no hay texto; «demo-reintento»: falla la primera vez).
import { createServer } from 'node:http';

const TEXT = 'Acta ficticia escaneada: la junta aprobó revisar la bajante del patio el 3 de marzo.';
const reader = { name: 'reader-stub', version: '1' };
const failedOnce = new Set();
const send = (res, status, body) => {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
};

function answer(res, body) {
  if (body.includes('demo-sin-texto')) return send(res, 200, { pages: [{ number: 1, text: '' }], reader });
  if (body.includes('demo-reintento') && !failedOnce.has('r')) {
    failedOnce.add('r');
    return send(res, 422, { error: 'ilegible' });
  }
  if (body.includes('demo-reintento')) return send(res, 200, { pages: [{ number: 1, text: '' }], reader });
  send(res, 200, { pages: [{ number: 1, text: TEXT }], reader });
}

createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') return send(res, 200, { status: 'ok', reader });
  if (req.method === 'POST' && req.url === '/read') {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    return req.on('end', () => answer(res, Buffer.concat(chunks)));
  }
  send(res, 404, { error: 'no encontrado' });
}).listen(8077, '0.0.0.0');
