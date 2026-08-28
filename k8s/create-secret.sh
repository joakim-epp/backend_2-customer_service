#!/bin/sh
# Creates the pensionat-secrets Secret that the Deployments read JWT_SECRET and ADMIN_PASSWORD
# from. Safe to rerun: an existing secret is replaced, not rejected.
set -eu

cd "$(dirname "$0")/.."

[ -f .env ] || {
    echo "No .env in $(pwd). Get the values from whoever set up the project." >&2
    exit 1
}

# -f2- keeps everything after the first =. Base64 values end in = and would be cut in half by -f2.
jwt_secret=$(grep '^JWT_SECRET=' .env | cut -d= -f2-)
admin_password=$(grep '^ADMIN_PASSWORD=' .env | cut -d= -f2-)

[ -n "$jwt_secret" ] || { echo "JWT_SECRET is missing or empty in .env" >&2; exit 1; }
[ -n "$admin_password" ] || { echo "ADMIN_PASSWORD is missing or empty in .env" >&2; exit 1; }

kubectl create secret generic pensionat-secrets \
    --from-literal=jwt-secret="$jwt_secret" \
    --from-literal=admin-password="$admin_password" \
    --dry-run=client -o yaml | kubectl apply -f -
