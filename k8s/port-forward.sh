#!/bin/sh
# Separate host ports allow Compose and Kubernetes to run at the same time.
set -eu
cd "$(dirname "$0")/.."
pidfile=.k8s-port-forward.pids
logfile=.k8s-port-forward.log

stop_forwards() {
    if [ -f "$pidfile" ]; then
        while read -r pid; do
            # Do not kill an unrelated process if an old PID has been reused.
            case "$(ps -p "$pid" -o command= 2>/dev/null || true)" in
                *kubectl*port-forward*svc/customer-service*|*kubectl*port-forward*svc/booking-service*|*kubectl*port-forward*svc/notification-service*)
                    kill "$pid" 2>/dev/null || true ;;
            esac
        done < "$pidfile"
        rm -f "$pidfile"
    fi
}
owned_pids=""
stop_owned_forwards() {
    for pid in $owned_pids; do
        kill "$pid" 2>/dev/null || true
    done
    # Another invocation may already have written its own PID file.
    if [ -f "$pidfile" ] && [ "$(tr '\n' ' ' < "$pidfile")" = "$owned_pids" ]; then
        rm -f "$pidfile"
    fi
}
stop_forwards
[ "${1:-}" = stop ] && { echo "Kubernetes port-forwards stopped."; exit 0; }
[ "$(kubectl config current-context)" = docker-desktop ] || {
    echo "Select docker-desktop before forwarding local services." >&2; exit 1;
}
trap stop_owned_forwards EXIT
trap 'exit 1' INT TERM
: > "$logfile"
for mapping in customer-service:18080:8080 booking-service:18081:8081 notification-service:18082:8082; do
    service=${mapping%%:*}
    ports=${mapping#*:}
    nohup kubectl port-forward "svc/$service" "$ports" >> "$logfile" 2>&1 < /dev/null &
    pid=$!
    owned_pids="$owned_pids$pid "
    echo "$pid" >> "$pidfile"
done
attempt=0
while [ "$(grep -c 'Forwarding from 127.0.0.1:' "$logfile" || true)" -lt 3 ]; do
    while read -r pid; do
        kill -0 "$pid" 2>/dev/null || { cat "$logfile" >&2; exit 1; }
    done < "$pidfile"
    attempt=$((attempt + 1))
    [ "$attempt" -lt 30 ] || { cat "$logfile" >&2; exit 1; }
    sleep 1
done
trap - EXIT INT TERM
echo "Kundtjänsten: http://localhost:18080"
echo "Bokningstjänsten: http://localhost:18081"
echo "Notifieringstjänsten: http://localhost:18082"
echo "Logga in med admin och ADMIN_PASSWORD från .env."
echo "Stoppa port-forwarding med ./k8s/port-forward.sh stop"

# Keep the parent alive when launched from an IDE task or an automation runner.
if [ "${1:-}" = --foreground ]; then
    trap stop_owned_forwards EXIT
    trap 'exit 1' INT TERM
    wait
fi
