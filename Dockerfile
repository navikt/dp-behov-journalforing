FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:867d4c6161a243e970e8242128860a20a9ff612d962c2b68003212e111caf40f

ENV TZ="Europe/Oslo"

COPY build/install/*/lib /app/lib

ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.nav.dagpenger.behov.journalforing.AppKt"]
