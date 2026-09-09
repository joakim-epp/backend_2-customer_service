#!/bin/sh
# Build and run all three services and databases in Docker Desktop Kubernetes.
set -eu
cd "$(dirname "$0")/.."

[ "$(kubectl config current-context)" = "docker-desktop" ] || {
    echo "Select the local cluster first: kubectl config use-context docker-desktop" >&2
    exit 1
}
case "${1:-}" in
    "") docker compose build customer-service booking-service notification-service ;;
    --skip-build) ;;
    *) echo "Usage: $0 [--skip-build]" >&2; exit 1 ;;
esac

# Unique tags avoid cached :latest images. Docker Desktop's kind nodes have
# their own containerd store, so import the images explicitly into every node.
image_tag="k8s-$(date +%Y%m%d%H%M%S)-$$"
for service in customer-service booking-service notification-service; do
    docker tag "$service:latest" "$service:$image_tag"
done
deploy_dir=$(mktemp -d "${TMPDIR:-/tmp}/pensionat-k8s.XXXXXX")
archive="$deploy_dir/images.tar"
trap 'rm -rf "$deploy_dir"' EXIT
trap 'exit 1' INT TERM
docker image save -o "$archive" \
    "customer-service:$image_tag" "booking-service:$image_tag" "notification-service:$image_tag"
for node in $(kubectl get nodes -o jsonpath='{.items[*].metadata.name}'); do
    if docker inspect "$node" >/dev/null 2>&1; then
        docker exec -i "$node" ctr --namespace k8s.io images import --snapshotter overlayfs - < "$archive"
    else
        echo "Node $node is not a Docker container. This script requires Docker Desktop with kind." >&2
        exit 1
    fi
done

./k8s/create-secret.sh
for service in customer booking notification; do
    kubectl apply -f "k8s/$service-db.yaml"
done
for service in customer booking notification; do
    kubectl rollout status "deployment/$service-db" --timeout=180s
done
for service in customer booking notification; do
    cp "k8s/$service-service.yaml" "$deploy_dir/"
done
cat > "$deploy_dir/kustomization.yaml" <<YAML
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - customer-service.yaml
  - booking-service.yaml
  - notification-service.yaml
images:
  - name: customer-service
    newTag: $image_tag
  - name: booking-service
    newTag: $image_tag
  - name: notification-service
    newTag: $image_tag
YAML
kubectl apply -k "$deploy_dir"
for service in customer booking notification; do
    kubectl rollout status "deployment/$service-service" --timeout=180s
done
kubectl get pods,pvc
./k8s/port-forward.sh
