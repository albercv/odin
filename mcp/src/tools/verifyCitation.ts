import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import type { ApiClient } from '../api/client.js';
import { guarded, jsonResult } from './result.js';

const DESCRIPTION =
  'Comprueba en una sola llamada todas las citas literales de tu respuesta (de 1 a 20), también las de documentos que no hayas leído con readPage. ' +
  'Devuelve verified para cada una, en el mismo orden. Si una da false, no la uses; una cita sin comprobar no se da. ' +
  'El título del documento no es texto de la página: solo vale lo que aparece en el texto. ' +
  'Si superseded o corrected es verdadero, la cita es de un documento que ya no es el vigente o que está corregido, y hay que decirlo.';

const citation = z.object({
  documentId: z.string().describe('Identificador del documento'),
  pageNumber: z.number().int().positive().describe('Número de página, desde 1'),
  quote: z.string().min(1).describe('Texto literal citado'),
});

export function registerVerifyCitation(server: McpServer, api: ApiClient): void {
  server.registerTool(
    'verifyCitation',
    { description: DESCRIPTION, inputSchema: { citations: z.array(citation).min(1).max(20) } },
    ({ citations }) =>
      guarded(async () => {
        const { results } = await api.verifyCitations({ citations });
        return jsonResult({ results: results.map((r, i) => ({ ...r, quote: citations[i]?.quote })) });
      }),
  );
}
