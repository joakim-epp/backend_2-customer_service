#!/bin/sh
# Create the Secret used by the three applications and their databases.
# .env is read as data, never sourced or printed. Use unquoted KEY=value lines.
# Required: JWT_SECRET, ADMIN_PASSWORD, CUSTOMER_DB_PASSWORD, BOOKING_DB_PASSWORD,
# NOTIFICATION_DB_PASSWORD. Keep current database passwords for existing volumes;
# changing a Secret does not rotate passwords already stored in PostgreSQL.
set -eu
cd "$(dirname "$0")/.."
[ -f .env ] || { echo "Create .env with all five variables listed at the top of this script." >&2; exit 1; }

read_value() {
    awk -v key="$1" '
        index($0, key "=") == 1 {
            count++
            value = substr($0, length(key) + 2)
            sub(/\r$/, "", value)
        }
        END {
            if (count != 1 || value == "") {
                print key " must have exactly one non-empty value in .env" > "/dev/stderr"
                exit 1
            }
            printf "%s", value
        }
    ' .env
}

# A private temporary directory keeps secrets out of command-line arguments.
umask 077
secret_dir=$(mktemp -d)
trap 'rm -rf "$secret_dir"' EXIT
trap 'exit 1' INT TERM
mkdir "$secret_dir/values"
for entry in \
    JWT_SECRET:jwt-secret \
    ADMIN_PASSWORD:admin-password \
    CUSTOMER_DB_PASSWORD:customer-db-password \
    BOOKING_DB_PASSWORD:booking-db-password \
    NOTIFICATION_DB_PASSWORD:notification-db-password; do
    read_value "${entry%%:*}" > "$secret_dir/values/${entry#*:}"
done

# Separate commands ensure a generation failure cannot be hidden by a pipeline.
kubectl create secret generic pensionat-secrets \
    --from-file="$secret_dir/values" --dry-run=client -o yaml > "$secret_dir/secret.yaml"
kubectl apply -f "$secret_dir/secret.yaml"
