import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import type { ApiClient } from '../api/client.js';
import { guarded, jsonResult } from './result.js';

export function registerReadPage(server: McpServer, api: ApiClient): void {
  server.registerTool(
    'readPage',
    {
      description:
        'Lee el texto literal de una página de un documento de la empresa. ' +
        'Si readerName es unlimited-ocr-2, la página es un escaneo leído por OCR y puede tener errores: cítala tal cual. ' +
        'Una página con el texto vacío no se ha leído: no concluyas nada de ella.',
      inputSchema: {
        documentId: z.string().describe('Identificador del documento'),
        pageNumber: z.number().int().positive().describe('Número de página, desde 1'),
      },
    },
    ({ documentId, pageNumber }) => guarded(async () => jsonResult(await api.readPage(documentId, pageNumber))),
  );
}
