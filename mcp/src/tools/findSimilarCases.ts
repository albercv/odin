import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import type { ApiClient, SimilarCasesResponse } from '../api/client.js';
import type { ProjectNames } from '../projects.js';
import { guarded, jsonResult, textResult } from './result.js';

const NO_CASES_MESSAGE = 'No constan casos parecidos en los documentos de tu empresa.';

const DESCRIPTION =
  'Busca casos ya resueltos parecidos a uno nuevo. ' +
  'Pasa en queries los términos del problema (qué falla, dónde, la causa) y sus reformulaciones. No pongas nombres de personas. ' +
  'De cada caso parecido, presenta su resolución y su resultado con la cita del documento y la página. ' +
  'Si dos casos se resolvieron de forma distinta, enséñalos los dos con sus citas y su resultado, y sugiere solo el que acabó bien, citando ese resultado. ' +
  'Sugiere una resolución solo si su resultado está escrito en el documento; si ninguno de los casos tiene el resultado escrito, enseña las resoluciones sin sugerir ninguna. ' +
  'Nunca compares ni menciones a las personas que resolvieron cada caso. ' +
  'Una ficha aportada es una ficha redactada con IA por un trabajador: preséntala como ficha, no como caso resuelto, y no le atribuyas un resultado que su texto no diga. ' +
  'addedAt es cuándo entró el documento en Odín, no la fecha del caso; esa fecha está en el texto. ' +
  'Si un caso no tiene resolución o resultado escritos, dilo; si es el mismo caso nuevo que te han dado, descártalo. ' +
  'superseded indica que un documento está sustituido; replacement es el documento vigente que lo sustituye (null si no puedes ver cuál). ' +
  'Una ficha con reviewStatus PENDING es tuya y está pendiente de revisión: dilo al citarla y recuerda que tus compañeros todavía no la ven.';

export function registerFindSimilarCases(server: McpServer, api: ApiClient, projects: ProjectNames): void {
  server.registerTool(
    'findSimilarCases',
    {
      description: DESCRIPTION,
      inputSchema: {
        queries: z.array(z.string()).min(1).describe('Términos del problema y sus reformulaciones, sin nombres de personas'),
        projectId: z.string().optional().describe('Limita los casos a un proyecto; también incluye lo que no tiene proyecto'),
        limit: z.number().int().positive().optional().describe('Máximo de casos'),
      },
    },
    (args) =>
      guarded(async () => {
        const result = await api.similarCases(args);
        if (result.noSource || !result.cases.length) return textResult(NO_CASES_MESSAGE);
        return jsonResult({ cases: result.cases.map((c) => toCitation(c, projects)) });
      }),
  );
}

type SimilarCase = SimilarCasesResponse['cases'][number];

// Una ficha lleva cardId y no documentId; ningún caso lleva autor (spec 5.2).
const toCitation = ({ sourceId, projectId, replacement, originLabel, body, ...rest }: SimilarCase, projects: ProjectNames) => ({
  ...rest,
  [rest.sourceType === 'KNOWLEDGE_CARD' ? 'cardId' : 'documentId']: sourceId,
  projectId: projectId ?? null,
  projectName: projects.nameOf(projectId),
  replacement: replacement ?? null,
  originLabel: originLabel ?? null,
  ...(body === undefined ? {} : { body }),
});
