#!/bin/sh
# Port-forwards the booking and customer services from the cluster in the background and prints
# the login. Rerun to restart, `./k8s/port-forward.sh stop` to tear down.
set -eu

cd "$(dirname "$0")/.."
pidfile=.port-forward.pids
logfile=.port-forward.log

if [ -f "$pidfile" ]; then
    xargs kill < "$pidfile" 2>/dev/null || true
    rm -f "$pidfile"
fi
[ "${1:-}" = "stop" ] && { echo "Port-forwards stopped."; exit 0; }

: > "$logfile"
for svc in booking-service:8081 customer-service:8080 notification-service:8082; do
    name=${svc%%:*}; port=${svc##*:}
    nohup kubectl port-forward "svc/$name" "$port:$port" >> "$logfile" 2>&1 &
    echo $! >> "$pidfile"
done
sleep 2
grep -q "Forwarding" "$logfile" || { cat "$logfile"; exit 1; }

echo "Bokningstjänsten     http://localhost:8081"
echo "Kundtjänsten         http://localhost:8080"
echo "Notifieringstjänsten http://localhost:8082"
echo
echo "Användarnamn admin"
echo "Lösenord     $(grep '^ADMIN_PASSWORD=' .env | cut -d= -f2-)"
echo
echo "Logg i $logfile. Stoppa med ./k8s/port-forward.sh stop"
