"""Authenticated Locust smoke against local GMS + Aiven OS3 search path.

Stock perf-test locustfiles omit auth headers; this stack has METADATA_SERVICE_AUTH_ENABLED.
"""

from __future__ import annotations

import base64
import json
import os
import urllib.request

from locust import HttpUser, between, events, task


def _login_token(frontend: str, user: str, password: str) -> str:
    req = urllib.request.Request(
        f"{frontend.rstrip('/')}/logIn",
        data=json.dumps({"username": user, "password": password}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req) as resp:
        for c in resp.headers.get_all("Set-Cookie") or []:
            if c.startswith("PLAY_SESSION="):
                val = c.split(";", 1)[0].split("=", 1)[1]
                pad = "=" * ((4 - len(val.split(".")[1]) % 4) % 4)
                return json.loads(
                    base64.urlsafe_b64decode(val.split(".")[1] + pad)
                )["data"]["token"]
    raise RuntimeError("login failed")


_TOKEN: str | None = None


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    global _TOKEN
    frontend = os.environ.get("DATAHUB_FRONTEND", "http://localhost:9002")
    user = os.environ.get("DATAHUB_USER", "datahub")
    password = os.environ.get("DATAHUB_PASSWORD", "datahub")
    _TOKEN = _login_token(frontend, user, password)
    print(f"Locust auth token acquired from {frontend}")


class AuthenticatedGmsUser(HttpUser):
    wait_time = between(0.2, 1.0)

    def on_start(self):
        if not _TOKEN:
            raise RuntimeError("no token")
        self.client.headers.update(
            {
                "Authorization": f"Bearer {_TOKEN}",
                "Content-Type": "application/json",
                "Accept": "application/json",
            }
        )

    @task(5)
    def restli_search(self):
        # Same shape as vendor/datahub/perf-test/locustfiles/search.py
        self.client.post(
            "/entities?action=search",
            data=json.dumps(
                {"input": "loadtest.search", "entity": "dataset", "start": 0, "count": 10}
            ),
            name="/entities?action=search",
        )

    @task(3)
    def graphql_search(self):
        q = {
            "query": (
                "query($q:String!){ search(input:{type:DATASET,query:$q,start:0,count:10})"
                "{ total } }"
            ),
            "variables": {"q": "loadtest"},
        }
        self.client.post("/api/graphql", data=json.dumps(q), name="/api/graphql search")

    @task(2)
    def graphql_lineage(self):
        q = {
            "query": (
                "query{ searchAcrossLineage(input:{"
                'urn:"urn:li:dataset:(urn:li:dataPlatform:snowflake,loadtest.lineage.hub,PROD)",'
                "direction:DOWNSTREAM,start:0,count:10}){ total } }"
            )
        }
        self.client.post("/api/graphql", data=json.dumps(q), name="/api/graphql lineage")

    @task(1)
    def browse(self):
        # Stock browse.py path; may be empty — still exercises OS browse query
        self.client.post(
            "/entities?action=browse",
            data=json.dumps(
                {"path": "/perf/test", "entity": "dataset", "start": 0, "limit": 10}
            ),
            name="/entities?action=browse",
        )
