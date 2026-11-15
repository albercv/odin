import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import type { ApiClient } from '../api/client.js';
import { WRITE_RULES } from './caseFile.js';
import type { ProjectTerms } from '../terms.js';
import { guarded, jsonResult, textResult } from './result.js';

const caseId = z.string().optional().describe('Identificador del caso');

function description(cases: boolean, projects: boolean, t: ProjectTerms): string {
  const target = [cases && 'al expediente de un caso', projects && `a ${t.one}`].filter(Boolean).join(' o ');
  return (
    `Pide un enlace para que el trabajador suba ficheros ${target}. ` +
    `${WRITE_RULES} ` +
    (cases && projects ? `Indica un caso o ${t.one}, no los dos. ` : '') +
    'El enlace abre una página de Odín que muestra el destino antes de subir, donde el trabajador elige los ficheros. ' +
    'Vale 15 minutos y solo funciona en la red local. ' +
    (projects ? `Un enlace de ${t.singular} sube solo a ${t.that} ${t.singular}; nunca a lo general de la empresa. ` : '') +
    'Da el enlace tal cual, sin cambiarlo, y di cuándo caduca (expiresAt).'
  );
}

export function registerUploadLink(server: McpServer, api: ApiClient, cases: boolean, projects: boolean, t: ProjectTerms): void {
  if (!cases && !projects) return;
  const inputSchema = { ...(cases && { caseId }), ...(projects && { projectId: z.string().optional().describe(`Identificador ${t.ofThe} ${t.singular}`) }) };
  server.registerTool(
    'requestUploadLink',
    { description: description(cases, projects, t), inputSchema },
    (args: { caseId?: string; projectId?: string }) =>
      guarded(async () => {
        if (args.caseId && args.projectId) return { ...textResult(`Indica un caso o ${t.one}, no los dos`), isError: true };
        if (args.caseId) return jsonResult(await api.requestUploadLink(args.caseId));
        if (args.projectId) return jsonResult(await api.requestProjectUploadLink(args.projectId));
        return { ...textResult(`Indica ${[cases && 'un caso', projects && t.one].filter(Boolean).join(' o ')}`), isError: true };
      }),
  );
}
