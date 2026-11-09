FROM node:24 AS build
WORKDIR /src/mcp
COPY mcp/package.json mcp/package-lock.json ./
RUN npm ci
COPY mcp/ ./
RUN npm run build && npm prune --omit=dev

FROM node:24-slim
WORKDIR /app
COPY --from=build /src/mcp/package.json ./
COPY --from=build /src/mcp/node_modules ./node_modules
COPY --from=build /src/mcp/dist ./dist
USER node
EXPOSE 8090
CMD ["node", "dist/http.js"]
