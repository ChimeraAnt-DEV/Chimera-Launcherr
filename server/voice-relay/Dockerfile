# Build stage: a static binary so the runtime image can be distroless/scratch-like.
FROM golang:1.23-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY *.go ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/voice-relay .

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/voice-relay /voice-relay
# UDP voice port and the HTTP health/metrics port.
EXPOSE 47902/udp
EXPOSE 8081/tcp
USER nonroot:nonroot
ENTRYPOINT ["/voice-relay"]
CMD ["-listen", ":47902", "-http-listen", ":8081"]
