# Docker API Guide

Ruta del proyecto:

`C:\Users\JeeP_\OneDrive\Escritorio\terra-api-v2\terra-api`

## Archivos

- `Dockerfile`
- `.dockerignore`
- `docker-compose.api.yml`

## Requisitos

1. Stack MariaDB levantado en `MariaDB-Docker`.
2. Red docker disponible: `mariadb-docker_default`.

## Build y run local

Usa variables desde `.env.dev`.

```bash
docker compose -f docker-compose.api.yml build terra-api
docker compose -f docker-compose.api.yml up -d terra-api
docker compose -f docker-compose.api.yml ps
docker logs -f terra-api
```

## Stop

```bash
docker compose -f docker-compose.api.yml down
```

## Variables importantes

- `API_HOST_PORT` (default `8080`)
- `DB_HOST_DOCKER` (default `l2-mariadb`)
- `DB_PORT_DOCKER` (default `3306`)
- `MARIADB_DOCKER_NETWORK` (default `mariadb-docker_default`)

## Nota

- El build Docker omite tests y Asciidoctor para evitar fallos de plugin en imagen.
