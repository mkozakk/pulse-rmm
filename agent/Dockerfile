FROM golang:1.24-alpine AS builder
WORKDIR /src
COPY agent/go.mod agent/go.sum ./
RUN go mod download
COPY agent/ .
RUN CGO_ENABLED=0 GOOS=linux go build -o /pulse-agent .

FROM alpine:3.21
RUN apk add --no-cache bash
RUN mkdir -p /var/lib/pulse-agent
COPY --from=builder /pulse-agent /usr/local/bin/pulse-agent
ENTRYPOINT ["pulse-agent"]
