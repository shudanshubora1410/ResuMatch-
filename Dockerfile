# ResuMatch - production image (used by Render).
#
# Stage 1 builds the WAR with Maven; stage 2 runs it on Tomcat 11 as a non-root user.
# Nothing secret is ever baked into the image: every credential arrives as an environment
# variable from the Render dashboard at run time.

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# Copy the pom on its own first so Maven's dependency download is cached in its own layer and a
# source-only change does not re-download the internet.
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -q -f backend/pom.xml dependency:go-offline || true

COPY backend/src backend/src
# The WAR packaging copies this folder into the web root (see maven-war-plugin webResources).
COPY frontend frontend

# Tests run in CI, not in the image build: a broken test must fail the pipeline, not the deploy.
RUN mvn -B -f backend/pom.xml clean package -DskipTests

# ---------- Stage 2: runtime ----------
# Tomcat 11 with a Java 17 runtime: the same Servlet 6.0 WAR also runs on Tomcat 10.1.
FROM tomcat:11.0-jre17 AS runtime

LABEL org.opencontainers.image.title="ResuMatch" \
      org.opencontainers.image.description="Explainable resume screening platform" \
      org.opencontainers.image.source="https://github.com/shudanshubara1410/resumatch"

ENV CATALINA_HOME=/usr/local/tomcat \
    CATALINA_TMPDIR=/tmp \
    PORT=8080 \
    APP_ENV=production \
    SUPPORT_EMAIL=support.resumatch@gmail.com \
    FILE_STORAGE=db \
    MAIL_PROVIDER=brevo \
    # Sized for Render's free 512 MB instance: leave headroom for the OS and the JVM's own overhead.
    JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:InitialRAMPercentage=25 -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -Xss512k -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom"

# Remove Tomcat's sample applications so ResuMatch is the only thing deployed.
RUN rm -rf ${CATALINA_HOME}/webapps/*

COPY --from=build /build/backend/target/resumatch.war ${CATALINA_HOME}/webapps/ROOT.war
COPY docker/entrypoint.sh /usr/local/bin/resumatch-entrypoint.sh

# The entrypoint rewrites conf/server.xml, and Tomcat writes to work/temp/logs, so the
# unprivileged "tomcat" user must own those directories.
RUN chmod 0755 /usr/local/bin/resumatch-entrypoint.sh \
    && mkdir -p ${CATALINA_HOME}/work ${CATALINA_HOME}/temp ${CATALINA_HOME}/logs \
    && chown -R tomcat:tomcat \
         ${CATALINA_HOME}/webapps \
         ${CATALINA_HOME}/work \
         ${CATALINA_HOME}/temp \
         ${CATALINA_HOME}/logs \
         ${CATALINA_HOME}/conf

# Security: never run the web server as root.
USER tomcat

EXPOSE 8080

# Render uses healthCheckPath instead; this keeps docker/compose honest too.
# Uses bash's /dev/tcp so no curl or wget has to be installed in the image.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/${PORT}' || exit 1

ENTRYPOINT ["/usr/local/bin/resumatch-entrypoint.sh"]
CMD ["catalina.sh", "run"]
