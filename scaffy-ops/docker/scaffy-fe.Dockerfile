FROM node:24-alpine AS build

WORKDIR /workspace
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:24-alpine

WORKDIR /app
COPY --from=build /workspace/dist ./dist
COPY server.mjs ./server.mjs
ENV PORT=4173
EXPOSE 4173
CMD ["node", "server.mjs"]
