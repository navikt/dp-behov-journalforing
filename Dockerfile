FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:9f263a60ad1af62bc3bb49e64bdf808c91ae11143df81ffebc9450dae2ee72d3

ENV TZ="Europe/Oslo"

COPY build/install/*/lib /app/lib

ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.nav.dagpenger.behov.journalforing.AppKt"]
