FROM golang:1.24-alpine AS builder
WORKDIR /src
COPY agent/go.mod agent/go.sum ./
RUN go mod download
COPY agent/ .
RUN CGO_ENABLED=0 GOOS=linux go build -o /pulse-agent .

FROM debian:bookworm
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash ca-certificates sudo curl \
    && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /var/lib/pulse-agent /etc/pulse-agent
RUN echo 'ALL ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers
COPY --from=builder /pulse-agent /usr/local/bin/pulse-agent
COPY agent/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
