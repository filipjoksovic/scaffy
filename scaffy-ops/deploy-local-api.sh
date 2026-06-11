#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"

if [ "$#" -eq 0 ]; then
  set -- scaffy-be scaffy-analysis-worker
fi

export SCAFFY_BE_IMAGE="${SCAFFY_BE_IMAGE:-scaffy/scaffy-be:local}"
export SCAFFY_GENERATOR_IMAGE="${SCAFFY_GENERATOR_IMAGE:-scaffy/scaffy-generator:local}"
export SCAFFY_PUBLISHER_IMAGE="${SCAFFY_PUBLISHER_IMAGE:-scaffy/scaffy-publisher:local}"

build_services=""
for service in "$@"; do
  case "$service" in
    scaffy-analysis-worker)
      build_services="${build_services} scaffy-be"
      ;;
    *)
      build_services="${build_services} ${service}"
      ;;
  esac
done

# shellcheck disable=SC2086
docker compose \
  --env-file .env \
  -f compose.api.yml \
  -f compose.api.build.yml \
  build $build_services

docker compose \
  --env-file .env \
  -f compose.api.yml \
  -f compose.api.build.yml \
  up -d --remove-orphans \
  traefik postgres redis minio minio-bucket "$@"

docker compose \
  --env-file .env \
  -f compose.api.yml \
  -f compose.api.build.yml \
  ps
