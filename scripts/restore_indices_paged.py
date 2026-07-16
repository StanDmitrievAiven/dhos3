#!/usr/bin/env python3
"""Page through OpenAPI restoreIndices until urnLike matches are exhausted."""

from __future__ import annotations

import argparse
import base64
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


def login_token(frontend: str, user: str, password: str) -> str:
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
    raise RuntimeError("no token")


def restore_page(gms: str, token: str, urn_like: str, start: int, batch_size: int, limit: int):
    q = urllib.parse.urlencode(
        {
            "urnLike": urn_like,
            "batchSize": batch_size,
            "start": start,
            "limit": limit,
        }
    )
    url = f"{gms.rstrip('/')}/openapi/operations/elasticSearch/restoreIndices?{q}"
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req, timeout=600) as resp:
        return json.loads(resp.read().decode())


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--gms", default="http://localhost:8080")
    p.add_argument("--frontend", default="http://localhost:9002")
    p.add_argument("--username", default="datahub")
    p.add_argument("--password", default="datahub")
    p.add_argument("--urn-like", required=True)
    p.add_argument("--batch-size", type=int, default=500)
    p.add_argument("--page-limit", type=int, default=500, help="rows per API call")
    p.add_argument("--start", type=int, default=0, help="SQL offset to resume from")
    p.add_argument("--max-rows", type=int, default=0, help="stop after N migrated (0=all)")
    args = p.parse_args()

    token = login_token(args.frontend, args.username, args.password)
    start = args.start
    total = 0
    t0 = time.time()
    while True:
        try:
            results = restore_page(
                args.gms, token, args.urn_like, start, args.batch_size, args.page_limit
            )
        except urllib.error.HTTPError as e:
            body = e.read().decode() if hasattr(e, "read") else ""
            print(f"HTTP {e.code} at start={start}: {body[:300]}", file=sys.stderr)
            # refresh token once on 401/403
            if e.code in (401, 403):
                token = login_token(args.frontend, args.username, args.password)
                continue
            return 1
        if not isinstance(results, list):
            print(f"unexpected response at start={start}: {results!r}"[:300], file=sys.stderr)
            return 1
        migrated = sum(r.get("rowsMigrated", 0) for r in results)
        if migrated == 0:
            break
        total += migrated
        start += migrated
        print(
            f"start→{start} +{migrated} (total {total}) last={results[-1].get('lastUrn','')[:80]}",
            flush=True,
        )
        if args.max_rows and total >= args.max_rows:
            break
        time.sleep(0.05)
    print(f"Done. rowsMigrated={total} in {time.time()-t0:.1f}s", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
