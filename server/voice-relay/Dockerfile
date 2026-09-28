# Stage 1: compile the relay into a static binary.
FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY *.go ./
# CGO off so the binary is static and runs on a bare Alpine image.
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/voice-relay .

# Stage 2: a small runtime image.
FROM alpine:latest
RUN adduser -D -H -u 10001 relay
COPY --from=build /out/voice-relay /usr/local/bin/voice-relay
USER relay
# The relay only needs its UDP voice port; it holds no state on disk.
EXPOSE 47902/udp
# Exec form (no shell) so the relay is PID 1 and receives Fly's SIGTERM directly.
ENTRYPOINT ["/usr/local/bin/voice-relay"]
CMD ["-listen", ":47902"]
