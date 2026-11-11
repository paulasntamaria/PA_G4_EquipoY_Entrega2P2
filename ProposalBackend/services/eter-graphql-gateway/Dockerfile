FROM node:22-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY src ./src
ENV NODE_ENV=production PORT=4000
EXPOSE 4000
HEALTHCHECK --interval=10s --timeout=3s CMD wget -qO- "http://localhost:4000/health" || exit 1
CMD ["node", "src/start.js"]
