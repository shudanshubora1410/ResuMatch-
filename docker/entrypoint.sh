#!/bin/sh
#
# Container entrypoint.
#
# Render assigns a port at run time through $PORT, while Tomcat's server.xml is baked into the
# image with 8080. Instead of maintaining a second server.xml, we patch the connector port on
# every start - small, obvious and impossible to forget when the port changes.

set -e

PORT="${PORT:-8080}"
SERVER_XML="${CATALINA_HOME}/conf/server.xml"

if [ ! -f "$SERVER_XML" ]; then
  echo "[entrypoint] ERROR: ${SERVER_XML} not found" >&2
  exit 1
fi

echo "[entrypoint] Starting ResuMatch on port ${PORT}"

# Replace only the connector port attribute; leave every other attribute untouched.
sed -i "s/port=\"8080\" protocol=\"HTTP\/1.1\"/port=\"${PORT}\" protocol=\"HTTP\/1.1\"/" "$SERVER_XML"

# Bind to all interfaces: the proxy in front of us is not on localhost.
sed -i "s/port=\"${PORT}\" protocol=\"HTTP\/1.1\"/port=\"${PORT}\" protocol=\"HTTP\/1.1\" address=\"0.0.0.0\"/" "$SERVER_XML"

# Shut down Tomcat cleanly on SIGTERM so in-flight requests (and pool connections) finish.
exec "$@"
