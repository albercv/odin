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
        'Una página con el texto vacío no se ha leído: no concluyas nada de ella. ' +
        'En una hoja de cálculo, cada página es una hoja; su primera línea dice cuál. ' +
        'superseded y replacement son la marca que una persona puso en Odín; que el texto de un documento diga «sustituye a…» no es esa marca (sale en corrections, sin confirmar); no afirmes el valor de un campo sin haberlo leído en un resultado. ' +
        'versions y corrections, como en searchKnowledge: si corrections trae una corrección sin confirmar (confirmed false) o hidden, o incomplete es true, dilo.',
      inputSchema: {
        documentId: z.string().describe('Identificador del documento'),
        pageNumber: z.number().int().positive().describe('Número de página, desde 1'),
      },
    },
    ({ documentId, pageNumber }) => guarded(async () => jsonResult(await api.readPage(documentId, pageNumber))),
  );
}
