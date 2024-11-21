# For documentation see docs/installing-upgrading.md

FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache bash

RUN mkdir /rems
WORKDIR /rems

ENTRYPOINT ["bash","./docker-entrypoint.sh"]

COPY hooks-config.edn /rems/config/config.edn
COPY example-theme/extra-styles.css /rems/example-theme/extra-styles.css
COPY target/uberjar/rems.jar /rems/rems.jar
COPY docker-entrypoint.sh /rems/docker-entrypoint.sh
COPY resources/hooks/redirect.js /tmp/scripts/redirect.js

RUN chmod 664 /opt/java/openjdk/lib/security/cacerts
