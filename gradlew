#!/usr/bin/env sh
APP_HOME=$(dirname "$0")
exec java $JAVA_OPTS -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
