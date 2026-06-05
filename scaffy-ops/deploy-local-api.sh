#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"

if [ "$#" -eq 0 ]; then
  set -- scaffy-be
fi

export SCAFFY_BE_IMAGE="${SCAFFY_BE_IMAGE:-scaffy/scaffy-be:local}"
export SCAFFY_GENERATOR_IMAGE="${SCAFFY_GENERATOR_IMAGE:-scaffy/scaffy-generator:local}"
export SCAFFY_PUBLISHER_IMAGE="${SCAFFY_PUBLISHER_IMAGE:-scaffy/scaffy-publisher:local}"

docker compose \
  --env-file .env \
  -f compose.api.yml \
  -f compose.api.build.yml \
  build "$@"

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
