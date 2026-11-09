FROM node:24 AS build
WORKDIR /src/panel
COPY panel/package.json panel/package-lock.json ./
RUN npm ci
COPY panel/ ./
RUN npm run build

FROM nginx:stable
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/panel/dist /usr/share/nginx/html
EXPOSE 80
