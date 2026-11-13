import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import type { ApiClient, MyCard } from '../api/client.js';
import type { ProjectNames } from '../projects.js';
import { guarded, jsonResult } from './result.js';

// Las instrucciones sobre la revisión van aquí y no en INSTRUCTIONS, que no tiene sitio (tope de 2.200).
const DESCRIPTION =
  'Lista las fichas que has guardado y en qué estado está su revisión. Si una está rechazada, enseña la nota del revisor tal cual. ' +
  'Si una ficha tuya tiene la decisión CORRECT, preséntala como corregida por el administrador y enseña los dos textos: el que enviaste y el corregido. ' +
  'Si una ficha tuya está rechazada, preséntala como rechazada y enseña la nota del revisor tal cual.';

export function registerListMyKnowledgeCards(server: McpServer, api: ApiClient, projects: ProjectNames): void {
  server.registerTool(
    'listMyKnowledgeCards',
    {
      description: DESCRIPTION,
      inputSchema: {
        status: z.enum(['PENDING', 'APPROVED', 'REJECTED']).optional().describe('Solo las fichas en este estado'),
      },
    },
    ({ status }) =>
      guarded(async () => {
        const cards = await api.myCards(status);
        await projects.refreshFor(cards.map((card) => card.projectId));
        return jsonResult({ cards: cards.map((card) => toCard(card, projects)) });
      }),
  );
}

const toCard = ({ id, projectId, originalBody, review, ...rest }: MyCard, projects: ProjectNames) => ({
  cardId: id,
  ...rest,
  originalBody: originalBody ?? null,
  projectId: projectId ?? null,
  projectName: projects.nameOf(projectId),
  review: review ?? null,
});
