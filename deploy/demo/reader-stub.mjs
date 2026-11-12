// Lector de prueba para `make demo`: no lee nada, devuelve siempre el mismo texto ficticio.
import { createServer } from 'node:http';

const TEXT = 'Acta ficticia escaneada: la junta aprobó revisar la bajante del patio el 3 de marzo.';
const reader = { name: 'reader-stub', version: '1' };
const send = (res, status, body) => {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
};

createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') return send(res, 200, { status: 'ok', reader });
  if (req.method === 'POST' && req.url === '/read') {
    req.resume();
    return req.on('end', () => send(res, 200, { pages: [{ number: 1, text: TEXT }], reader }));
  }
  send(res, 404, { error: 'no encontrado' });
}).listen(8077, '0.0.0.0');
