# Stage 1: Build React + TypeScript bundle
FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json ./
RUN npm install
COPY tsconfig.json vite.config.ts index.html ./
COPY src ./src
RUN npm run build

# Stage 2: Serve static bundle & reverse-proxy API via Nginx
FROM nginx:1.27-alpine
ENV BACKEND_UPSTREAM=nms-backend:8080
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
