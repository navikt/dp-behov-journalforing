FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:ca23e69d0c8ee495aa6be12118c974eabdff4622585bad825474a4af605bc8c2

ENV TZ="Europe/Oslo"

COPY build/install/*/lib /app/lib

ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.nav.dagpenger.behov.journalforing.AppKt"]
