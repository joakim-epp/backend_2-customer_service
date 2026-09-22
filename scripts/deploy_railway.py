import json
import os
import subprocess
import time
import urllib.error
import urllib.request

INSTANCE_QUERY = """
query($serviceId: String!, $environmentId: String!) {
  serviceInstance(serviceId: $serviceId, environmentId: $environmentId) {
    healthcheckPath
    source { image }
  }
}
"""
DEPLOY_MUTATION = """
mutation($serviceId: String!, $environmentId: String!) {
  serviceInstanceDeployV2(serviceId: $serviceId, environmentId: $environmentId)
}
"""
STATUS_QUERY = """
query($id: String!) {
  deployment(id: $id) { id status deploymentStopped }
}
"""
TERMINAL_FAILURES = {
    "FAILED", "CRASHED", "REMOVED", "REMOVING", "SKIPPED", "SLEEPING", "NEEDS_APPROVAL"
}


def railway_api(query, variables):
    result = subprocess.run(
        ["railway", "api", query, "--variables", json.dumps(variables), "--compact"],
        check=True, capture_output=True, text=True, timeout=45,
    )
    response = json.loads(result.stdout)
    if response.get("errors") or not response.get("data"):
        raise RuntimeError("Railway API did not return successful data")
    return response["data"]


def health_is_up(url):
    try:
        request = urllib.request.Request(url, headers={"Cache-Control": "no-cache"})
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status == 200 and json.load(response).get("status") == "UP"
    except (urllib.error.URLError, TimeoutError, ValueError):
        return False


def wait_for_deployment(deployment_id, health_url, timeout=600):
    deadline = time.monotonic() + timeout
    previous_status = None
    while time.monotonic() < deadline:
        deployment = railway_api(STATUS_QUERY, {"id": deployment_id})["deployment"]
        if deployment["id"] != deployment_id:
            raise RuntimeError("Railway returned a different deployment")
        status = deployment["status"]
        if status != previous_status:
            print(f"Deployment {deployment_id}: {status}", flush=True)
            previous_status = status
        if status in TERMINAL_FAILURES or (status == "SUCCESS" and deployment["deploymentStopped"]):
            raise RuntimeError(f"Deployment {deployment_id} failed: {status}")
        if status == "SUCCESS" and health_is_up(health_url):
            print(f"Deployment {deployment_id} is healthy at {health_url}", flush=True)
            return
        time.sleep(5)
    raise TimeoutError(f"Deployment {deployment_id} did not become healthy within {timeout}s")


def deploy(service_id, environment_id, health_url):
    variables = {"serviceId": service_id, "environmentId": environment_id}
    instance = railway_api(INSTANCE_QUERY, variables)["serviceInstance"]
    if not instance["healthcheckPath"]:
        raise RuntimeError("Configure a Railway health check before deploying")
    if not (instance["source"] or {}).get("image"):
        raise RuntimeError("Expected a Railway service configured with a Docker image")
    deployment_id = railway_api(DEPLOY_MUTATION, variables)["serviceInstanceDeployV2"]
    if not isinstance(deployment_id, str) or not deployment_id:
        raise RuntimeError("Railway did not return a deployment ID")
    wait_for_deployment(deployment_id, health_url)


if __name__ == "__main__":
    try:
        deploy(os.environ["RAILWAY_SERVICE_ID"], os.environ["RAILWAY_ENVIRONMENT_ID"],
               os.environ["RAILWAY_HEALTH_URL"])
    except (KeyError, RuntimeError, TimeoutError, ValueError, subprocess.SubprocessError) as error:
        raise SystemExit(f"Deployment verification failed: {error}") from error
