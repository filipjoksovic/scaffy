FROM node:24-bookworm

WORKDIR /app

COPY package*.json ./
RUN npm install

COPY tsconfig.json ./
COPY src ./src
RUN npm run build

CMD ["npm", "run", "start"]
