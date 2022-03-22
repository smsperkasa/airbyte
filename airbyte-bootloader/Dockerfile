ARG JDK_VERSION=17.0.1
FROM openjdk:${JDK_VERSION}-slim

ENV APPLICATION airbyte-bootloader
ARG VERSION=0.35.59-alpha

WORKDIR /app

ADD bin/${APPLICATION}-${VERSION}.tar /app

ENTRYPOINT ["/bin/bash", "-c", "${APPLICATION}-${VERSION}/bin/${APPLICATION}"]
