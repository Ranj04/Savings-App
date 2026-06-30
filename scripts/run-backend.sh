#!/usr/bin/env sh
# Launch the Java backend for `npm run dev`, resolving a Java 21 home so users
# don't have to edit their shell profile. Homebrew's openjdk@21 is keg-only
# (not symlinked into the system), which is why a bare `mvn` often fails with
# "Unable to locate a Java Runtime".
set -e

if [ -z "$JAVA_HOME" ]; then
  # macOS: ask the system for a registered JDK 21 first.
  if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
  fi
  # Fall back to common Homebrew locations (Apple Silicon, then Intel).
  [ -z "$JAVA_HOME" ] && [ -d /opt/homebrew/opt/openjdk@21 ] && JAVA_HOME=/opt/homebrew/opt/openjdk@21
  [ -z "$JAVA_HOME" ] && [ -d /usr/local/opt/openjdk@21 ] && JAVA_HOME=/usr/local/opt/openjdk@21
fi

if [ -n "$JAVA_HOME" ]; then
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if ! command -v java >/dev/null 2>&1; then
  echo "✗ No Java 21 runtime found. Install one with:  brew install openjdk@21" >&2
  echo "  (or set JAVA_HOME to an existing JDK 21)" >&2
  exit 1
fi

exec mvn -q -f back-end/pom.xml compile exec:java
