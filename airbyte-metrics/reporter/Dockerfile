ARG JDK_VERSION=17.0.1
FROM openjdk:${JDK_VERSION}-slim AS metrics-reporter

ENV APPLICATION airbyte-metrics-reporter
ARG VERSION=0.35.59-alpha

WORKDIR /app

ADD bin/${APPLICATION}-${VERSION}.tar /app

ARG VERSION

# wait for upstream dependencies to become available before starting server
ENTRYPOINT ["/bin/bash", "-c", "${APPLICATION}-${VERSION}/bin/${APPLICATION}"]
