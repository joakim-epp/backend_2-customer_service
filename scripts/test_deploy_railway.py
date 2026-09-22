import io
import json
import unittest
import urllib.error
from unittest.mock import patch

import deploy_railway as deploy


class DeploymentTests(unittest.TestCase):
    def deployment(self, status, stopped=False, identifier="new-deployment"):
        return {"deployment": {"id": identifier, "status": status, "deploymentStopped": stopped}}

    @patch.object(deploy.time, "sleep")
    @patch.object(deploy, "health_is_up", return_value=True)
    @patch.object(deploy, "railway_api")
    def test_waits_for_new_deployment_before_checking_health(self, api, health, sleep):
        api.side_effect = [self.deployment("DEPLOYING"), self.deployment("SUCCESS")]
        deploy.wait_for_deployment("new-deployment", "https://example.test/health")
        self.assertEqual(api.call_count, 2)
        for call in api.call_args_list:
            self.assertEqual(call.args[1], {"id": "new-deployment"})
        health.assert_called_once()

    @patch.object(deploy, "health_is_up")
    @patch.object(deploy, "railway_api")
    def test_failed_or_stopped_deployment_cannot_pass(self, api, health):
        for status in deploy.TERMINAL_FAILURES:
            with self.subTest(status=status):
                api.return_value = self.deployment(status)
                with self.assertRaises(RuntimeError):
                    deploy.wait_for_deployment("new-deployment", "https://example.test/health")
        api.return_value = self.deployment("SUCCESS", stopped=True)
        with self.assertRaises(RuntimeError):
            deploy.wait_for_deployment("new-deployment", "https://example.test/health")
        health.assert_not_called()

    @patch.object(deploy, "railway_api")
    def test_previous_deployment_cannot_satisfy_verification(self, api):
        api.return_value = self.deployment("SUCCESS", identifier="old-deployment")
        with self.assertRaisesRegex(RuntimeError, "different deployment"):
            deploy.wait_for_deployment("new-deployment", "https://example.test/health")

    @patch.object(deploy.time, "monotonic", side_effect=[0, 1, 601])
    @patch.object(deploy.time, "sleep")
    @patch.object(deploy, "health_is_up", return_value=False)
    @patch.object(deploy, "railway_api")
    def test_times_out_when_public_endpoint_is_unhealthy(self, api, health, sleep, clock):
        api.return_value = self.deployment("SUCCESS")
        with self.assertRaises(TimeoutError):
            deploy.wait_for_deployment("new-deployment", "https://example.test/health")

    @patch.object(deploy.time, "sleep")
    @patch.object(deploy, "health_is_up", side_effect=[False, True])
    @patch.object(deploy, "railway_api")
    def test_retries_endpoint_while_new_deployment_is_running(self, api, health, sleep):
        api.return_value = self.deployment("SUCCESS")
        deploy.wait_for_deployment("new-deployment", "https://example.test/health")
        self.assertEqual(health.call_count, 2)

    @patch.object(deploy, "wait_for_deployment")
    @patch.object(deploy, "railway_api")
    def test_tracks_id_returned_by_deployment_request(self, api, wait):
        api.side_effect = [
            {"serviceInstance": {"healthcheckPath": "/health", "source": {"image": "image:latest"}}},
            {"serviceInstanceUpdate": True},
            {"serviceInstanceDeployV2": "new-deployment"},
        ]
        deploy.deploy("service", "environment", "https://example.test/health", "example/service:build-42.1")
        wait.assert_called_once_with("new-deployment", "https://example.test/health")
        self.assertEqual(api.call_args_list[1].args[0], deploy.UPDATE_IMAGE_MUTATION)
        self.assertEqual(api.call_args_list[1].args[1]["image"], "example/service:build-42.1")
        self.assertEqual(api.call_args_list[2].args[0], deploy.DEPLOY_MUTATION)

    @patch.object(deploy, "railway_api")
    def test_missing_health_check_prevents_deployment(self, api):
        api.return_value = {"serviceInstance": {"healthcheckPath": None, "source": {"image": "image"}}}
        with self.assertRaises(RuntimeError):
            deploy.deploy("service", "environment", "https://example.test/health", "example/service:build-42.1")
        self.assertEqual(api.call_count, 1)

    @patch.object(deploy, "railway_api")
    def test_missing_image_source_prevents_deployment(self, api):
        api.return_value = {"serviceInstance": {"healthcheckPath": "/health", "source": None}}
        with self.assertRaises(RuntimeError):
            deploy.deploy("service", "environment", "https://example.test/health", "example/service:build-42.1")
        self.assertEqual(api.call_count, 1)

    @patch.object(deploy.subprocess, "run")
    def test_graphql_errors_fail_verification(self, run):
        run.return_value.stdout = json.dumps({"errors": [{"message": "denied"}]})
        with self.assertRaises(RuntimeError):
            deploy.railway_api(deploy.STATUS_QUERY, {"id": "new-deployment"})

    @patch.object(deploy.urllib.request, "urlopen")
    def test_health_requires_http_200_and_up(self, urlopen):
        for status, body, expected in [(200, b'{"status":"UP"}', True),
                                       (200, b'{"status":"DOWN"}', False),
                                       (200, b'not-json', False), (503, b'{}', False)]:
            with self.subTest(status=status, body=body):
                response = io.BytesIO(body)
                response.status = status
                urlopen.return_value = response
                self.assertEqual(deploy.health_is_up("https://example.test/health"), expected)
        urlopen.side_effect = urllib.error.URLError("unreachable")
        self.assertFalse(deploy.health_is_up("https://example.test/health"))

    @patch.object(deploy, "railway_api")
    def test_unversioned_image_is_rejected_before_api_calls(self, api):
        for image in ["example/service", "example/service:", "example/service:latest"]:
            with self.subTest(image=image), self.assertRaises(RuntimeError):
                deploy.deploy("service", "environment", "https://example.test/health", image)
        api.assert_not_called()

    @patch.object(deploy, "railway_api")
    def test_failed_image_update_prevents_deployment(self, api):
        api.side_effect = [
            {"serviceInstance": {"healthcheckPath": "/health", "source": {"image": "old-image"}}},
            {"serviceInstanceUpdate": False},
        ]
        with self.assertRaises(RuntimeError):
            deploy.deploy("service", "environment", "https://example.test/health", "example/service:build-42.1")
        self.assertEqual(api.call_count, 2)
