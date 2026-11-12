// Etiquetador de prueba para `make demo`: no usa ningún modelo. Habla como Ollama y responde según palabras del texto.
import { createServer } from 'node:http';

const send = (res, status, body) => {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
};

// El prompt es «instrucciones y lista de temas, línea en blanco, texto, línea en blanco, regla final»: solo el texto decide.
const textOf = (prompt) => prompt.split('\n\n').slice(1, -1).join('\n\n');
const answerFor = (text) => (/filtración|fuga/i.test(text) ? 'agua y humedades' : 'otros');

createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/api/tags') return send(res, 200, { models: [{ name: 'stub' }] });
  if (req.method === 'POST' && req.url === '/api/generate') {
    const chunks = [];
    req.on('data', (chunk) => chunks.push(chunk));
    return req.on('end', () => {
      const { prompt } = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      send(res, 200, { response: answerFor(textOf(prompt)), prompt_eval_count: 100 });
    });
  }
  send(res, 404, { error: 'no encontrado' });
}).listen(11434, '0.0.0.0');
