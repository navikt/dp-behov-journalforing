FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:ef85db321b742cb3b1c11b3c68f6f512ce7f7cb2b0ba2f50ae96771b239b7e75

ENV TZ="Europe/Oslo"

COPY build/install/*/lib /app/lib

ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.nav.dagpenger.behov.journalforing.AppKt"]
