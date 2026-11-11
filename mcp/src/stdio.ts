import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { createApiClient } from './api/client.js';
import { buildServer } from './server.js';

const apiUrl = process.env.ODIN_API_URL;
const accessKey = process.env.ODIN_ACCESS_KEY;
if (!apiUrl || !accessKey) {
  console.error('Faltan ODIN_API_URL y ODIN_ACCESS_KEY en el entorno.');
  process.exit(1);
}
try {
  const server = await buildServer(createApiClient(apiUrl, accessKey));
  await server.connect(new StdioServerTransport());
} catch (error) {
  console.error(`No se pudo iniciar el servidor MCP: ${error instanceof Error ? error.message : 'error desconocido'}`);
  process.exit(1);
}
