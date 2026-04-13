#!/bin/sh
# Gradle wrapper script

APP_HOME=$( cd "${APP_HOME:-./}" > /dev/null && pwd -P ) || exit
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" \
    ${DEFAULT_JVM_OPTS} \
    ${JAVA_OPTS} \
    ${GRADLE_OPTS} \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
