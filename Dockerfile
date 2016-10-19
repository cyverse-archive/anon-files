FROM discoenv/clojure-base:master

ENV CONF_TEMPLATE=/usr/src/app/anon-files.properties.tmpl
ENV CONF_FILENAME=anon-files.properties
ENV PROGRAM=anon-files

VOLUME ["/etc/iplant/de"]

COPY project.clj /usr/src/app/
RUN lein deps

COPY conf/main/logback.xml /usr/src/app/
COPY . /usr/src/app

RUN lein uberjar && \
    cp target/anon-files-standalone.jar .

RUN ln -s "/usr/bin/java" "/bin/anon-files"

ENTRYPOINT ["run-service", "-Dlogback.configurationFile=/etc/iplant/de/logging/anon-files-logging.xml", "-cp", ".:anon-files-standalone.jar", "anon_files.core"]

ARG git_commit=unknown
ARG version=unknown

LABEL org.cyverse.git-ref="$git_commit"
LABEL org.cyverse.version="$version"
