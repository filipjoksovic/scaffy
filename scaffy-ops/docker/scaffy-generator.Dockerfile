FROM node:24-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl git unzip zip openjdk-21-jdk maven \
    && rm -rf /var/lib/apt/lists/*

ENV DOTNET_ROOT=/usr/share/dotnet
ENV PATH="${PATH}:${DOTNET_ROOT}"
RUN curl -fsSL https://dot.net/v1/dotnet-install.sh -o /tmp/dotnet-install.sh \
    && chmod +x /tmp/dotnet-install.sh \
    && /tmp/dotnet-install.sh --channel 8.0 --install-dir ${DOTNET_ROOT} \
    && /tmp/dotnet-install.sh --channel 9.0 --install-dir ${DOTNET_ROOT} \
    && /tmp/dotnet-install.sh --channel 10.0 --install-dir ${DOTNET_ROOT} \
    && rm /tmp/dotnet-install.sh

WORKDIR /app

COPY package*.json ./
RUN npm install

COPY tsconfig.json ./
COPY src ./src
RUN npm run build

CMD ["npm", "run", "start"]
