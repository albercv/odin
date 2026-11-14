import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import type { ApiClient } from '../api/client.js';
import { WRITE_RULES } from './caseFile.js';
import { guarded, jsonResult, textResult } from './result.js';

const caseId = z.string().optional().describe('Identificador del caso');
const projectId = z.string().optional().describe('Identificador del proyecto');

function description(cases: boolean, projects: boolean): string {
  const target = [cases && 'al expediente de un caso', projects && 'a un proyecto'].filter(Boolean).join(' o ');
  return (
    `Pide un enlace para que el trabajador suba ficheros ${target}. ` +
    `${WRITE_RULES} ` +
    (cases && projects ? 'Indica un caso o un proyecto, no los dos. ' : '') +
    'El enlace abre una página de Odín que muestra el destino antes de subir, donde el trabajador elige los ficheros. ' +
    'Vale 15 minutos y solo funciona en la red de la oficina. ' +
    'Desde el chat no se sube a la zona general de la empresa. ' +
    'Da el enlace tal cual, sin cambiarlo, y di cuándo caduca (expiresAt).'
  );
}

export function registerUploadLink(server: McpServer, api: ApiClient, cases: boolean, projects: boolean): void {
  if (!cases && !projects) return;
  const inputSchema = { ...(cases && { caseId }), ...(projects && { projectId }) };
  server.registerTool(
    'requestUploadLink',
    { description: description(cases, projects), inputSchema },
    (args: { caseId?: string; projectId?: string }) =>
      guarded(async () => {
        if (args.caseId && args.projectId) return { ...textResult('Indica un caso o un proyecto, no los dos'), isError: true };
        if (args.caseId) return jsonResult(await api.requestUploadLink(args.caseId));
        if (args.projectId) return jsonResult(await api.requestProjectUploadLink(args.projectId));
        return { ...textResult(`Indica ${[cases && 'un caso', projects && 'un proyecto'].filter(Boolean).join(' o ')}`), isError: true };
      }),
  );
}
