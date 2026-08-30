# ---- Build stage: compile the Vite app ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
# Use npm install (no lockfile required); switch to `npm ci` once you commit package-lock.json.
RUN npm install
COPY . .
# Vite bakes env vars prefixed with VITE_ at build time.
ARG VITE_API_BASE_URL=http://localhost:8080/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ---- Run stage: serve static files with nginx ----
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
