#!/usr/bin/env bash
# Starts the Spring Boot application, then times its SQL and HTTP traffic by attaching
# to the running process, and finally removes the hooks again.
#   ./demo.sh           run the demo
set -euo pipefail
cd "$(dirname "$0")"
root=../..

"$root/gradlew" -q -p "$root" :tendril-agent-all:shadowJar
"$root/gradlew" -q -p . :app:bootJar :hooks:jar
agent=$(ls "$root"/tendril-agent-all/build/libs/tendril-agent-all-*.jar | head -1)

say() { printf '\n\033[1m$ %s\033[0m\n' "$*"; }
get() { say "curl localhost:8089$1"; curl -s "localhost:8089$1"; echo; sleep 0.4; }

say "java -jar app.jar &"
java -XX:+EnableDynamicAgentLoading -Xshare:off -jar app/build/libs/app.jar &
app=$!
trap 'kill $app 2>/dev/null || true' EXIT
until curl -s -o /dev/null localhost:8089/report; do sleep 0.2; done
echo "(Spring Boot is up, pid $app)"

get /orders/ada

say "java -jar tendril-agent-all.jar attach $app --hooks timing.Timing --classpath hooks.jar"
java -jar "$agent" attach "$app" --hooks timing.Timing --classpath hooks/build/libs/hooks.jar

get /orders/ada
get /report

say "java -jar tendril-agent-all.jar detach $app"
java -jar "$agent" detach "$app"

get /orders/ada
