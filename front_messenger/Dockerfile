# ── Этап сборки ──────────────────────────────────────────────────────────────
FROM node:18-alpine AS build
WORKDIR /app

# Копируем зависимости отдельно для кэширования слоя
COPY package.json package-lock.json ./
RUN npm install --legacy-peer-deps

# Копируем исходники
COPY . .

# Передаём URL Gateway как переменную сборки
ARG REACT_APP_GATEWAY_URL=http://localhost:8083
ARG REACT_APP_WS_URL=ws://localhost:8083
ENV REACT_APP_GATEWAY_URL=$REACT_APP_GATEWAY_URL
ENV REACT_APP_WS_URL=$REACT_APP_WS_URL

# Собираем (craco build)
RUN npm run build

# ── Этап продакшн (nginx) ─────────────────────────────────────────────────────
FROM nginx:1.25-alpine
WORKDIR /app

# Копируем собранный билд
COPY --from=build /app/build /usr/share/nginx/html

# Копируем конфиг nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
