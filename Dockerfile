FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:6f9890a535b1eb00ca9e0e8ee0290f39427d2ae8e5ea02455df5522cea6c28f1

ENV TZ="Europe/Oslo"

COPY build/install/*/lib /app/lib

ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.nav.dagpenger.behov.journalforing.AppKt"]
