FROM clojure:alpine

RUN apk add --update git && \
    rm -rf /var/cache/apk

VOLUME ["/etc/iplant/de"]

WORKDIR /usr/src/app

COPY project.clj /usr/src/app/
RUN lein deps

COPY LICENSE /usr/src/app/
COPY conf/main/logback.xml /usr/src/app
COPY README.md /usr/src/app/
COPY src /usr/src/app/src
COPY test /usr/src/app/test
COPY .git /usr/src/app/.git

RUN lein uberjar && \
    cp target/anon-files-standalone.jar . && \
    lein clean

RUN ln -s "/usr/bin/java" "/bin/anon-files"

ENTRYPOINT ["anon-files", "-Dlogback.configurationFile=/etc/iplant/de/logging/anon-files-logging.xml", "-cp", ".:anon-files-standalone.jar", "anon_files.core"]
CMD ["--help"]

ARG git_commit=unknown
ARG version=unknown

LABEL org.cyverse.git-ref="$git_commit"
LABEL org.cyverse.version="$version"
