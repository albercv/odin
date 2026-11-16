import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';
import { z } from 'zod';
import { ApiError, type ApiClient } from '../api/client.js';
import { errorMessage, jsonResult } from './result.js';

const MAX_RANGE_MS = 7 * 24 * 3600 * 1000;
const FILTERS = ['companyId', 'projectId', 'action', 'actorUserId', 'channel', 'outcome', 'cursor'] as const;

const MESSAGES: Record<number, string> = {
  400: 'La consulta no es válida: indica «from» y «to» como fechas ISO, con 7 días como máximo entre las dos.',
  403: 'Esa consulta queda fuera de tu ámbito de administrador, o la consulta desde el chat está desactivada.',
  409: 'La consulta desde el chat no está activada en Odín.',
  429: 'Has llegado al tope diario de 500 entradas. Vuelve mañana (día UTC).',
};

export function registerListActivity(server: McpServer, api: ApiClient): void {
  server.registerTool(
    'listActivity',
    {
      description:
        'Devuelve hechos del registro de actividad del administrador: quién hizo qué y cuándo. ' +
        'Cuéntalos tal cual constan; no valores, compares, ordenes ni cuentes a las personas, ni deduzcas intenciones. ' +
        'El intervalo (from, to; fechas ISO) es de 7 días como máximo por llamada. ' +
        'Cada llamada devuelve 50 entradas como máximo (sigue con nextCursor) y hay un tope de 500 al día. ' +
        'Lo que consultes llega a la IA del administrador, que es un tercero.',
      inputSchema: {
        from: z.iso.datetime({ offset: true }).describe('Inicio del intervalo (ISO-8601)'),
        to: z.iso.datetime({ offset: true }).describe('Fin del intervalo, exclusivo (ISO-8601)'),
        companyId: z.string().optional().describe('Limita a una empresa'),
        projectId: z.string().optional().describe('Limita a un proyecto'),
        action: z.string().optional().describe('Limita a un tipo de acción'),
        actorUserId: z.string().optional().describe('Limita a los hechos de una persona, por su id'),
        channel: z.string().optional().describe('Limita a un canal'),
        outcome: z.string().optional().describe('Limita a un resultado'),
        cursor: z.string().optional().describe('nextCursor de la llamada anterior'),
      },
    },
    (args) => run(api, args),
  );
}

async function run(api: ApiClient, args: { from: string; to: string } & Partial<Record<(typeof FILTERS)[number], string>>) {
  if (Date.parse(args.to) - Date.parse(args.from) > MAX_RANGE_MS) return failure('El intervalo es de 7 días como máximo por llamada.');
  const query = new URLSearchParams({ from: args.from, to: args.to });
  for (const key of FILTERS) if (args[key]) query.set(key, args[key]);
  try {
    return jsonResult(await api.activity(query.toString()));
  } catch (error) {
    return failure(error instanceof ApiError && MESSAGES[error.status] ? MESSAGES[error.status]! : errorMessage(error));
  }
}

const failure = (text: string): CallToolResult => ({ content: [{ type: 'text', text }], isError: true });
