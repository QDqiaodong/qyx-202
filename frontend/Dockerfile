FROM ${DOCKER_REGISTRY:-docker.io/library/}node:20-alpine AS builder

WORKDIR /app

COPY package*.json ./

RUN npm config set registry https://registry.npmmirror.com/ || npm config set registry https://mirrors.ustc.edu.cn/npm/ || npm config set registry https://registry.npmjs.org/
RUN npm ci

COPY . .

RUN npm run build

FROM ${DOCKER_REGISTRY:-docker.io/library/}nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html

COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]