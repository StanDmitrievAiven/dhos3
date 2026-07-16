#!/usr/bin/env python3
"""Create a lineage hub dataset with wide table-level upstream/downstream fan-out.

Open in UI (Lineage tab, expand upstream + downstream):
  urn:li:dataset:(urn:li:dataPlatform:snowflake,loadtest.lineage.hub,PROD)
"""

from __future__ import annotations

import argparse
import base64
import json
import sys
import time
import urllib.request
from typing import Iterable, List

from datahub.emitter.mce_builder import make_dataset_urn
from datahub.emitter.mcp import MetadataChangeProposalWrapper
from datahub.emitter.rest_emitter import DatahubRestEmitter, EmitMode
from datahub.metadata.schema_classes import (
    DatasetLineageTypeClass,
    DatasetPropertiesClass,
    StatusClass,
    UpstreamClass,
    UpstreamLineageClass,
)


def login_token(frontend_url: str, username: str, password: str) -> str:
    req = urllib.request.Request(
        f"{frontend_url.rstrip('/')}/logIn",
        data=json.dumps({"username": username, "password": password}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req) as resp:
        cookies = resp.headers.get_all("Set-Cookie") or []
    for c in cookies:
        if c.startswith("PLAY_SESSION="):
            val = c.split(";", 1)[0].split("=", 1)[1]
            pad = "=" * ((4 - len(val.split(".")[1]) % 4) % 4)
            payload = json.loads(
                base64.urlsafe_b64decode(val.split(".")[1] + pad)
            )
            return payload["data"]["token"]
    raise RuntimeError("login token missing")


def base_mcps(urn: str, name: str, description: str) -> List[MetadataChangeProposalWrapper]:
    return [
        MetadataChangeProposalWrapper(entityUrn=urn, aspect=StatusClass(removed=False)),
        MetadataChangeProposalWrapper(
            entityUrn=urn,
            aspect=DatasetPropertiesClass(name=name, description=description),
        ),
    ]


def lineage_mcps(
    platform: str, env: str, upstream_count: int, downstream_count: int
) -> Iterable[MetadataChangeProposalWrapper]:
    hub_name = "loadtest.lineage.hub"
    hub_urn = make_dataset_urn(platform, hub_name, env)
    upstream_urns = [
        make_dataset_urn(platform, f"loadtest.lineage.upstream_{i:03d}", env)
        for i in range(upstream_count)
    ]
    downstream_urns = [
        make_dataset_urn(platform, f"loadtest.lineage.downstream_{i:03d}", env)
        for i in range(downstream_count)
    ]

    yield from base_mcps(
        hub_urn,
        hub_name,
        f"Lineage hub — expand UPSTREAM ({upstream_count}) and DOWNSTREAM ({downstream_count})",
    )
    for i, urn in enumerate(upstream_urns):
        yield from base_mcps(
            urn,
            f"loadtest.lineage.upstream_{i:03d}",
            f"Upstream neighbor #{i} of lineage hub",
        )
    for i, urn in enumerate(downstream_urns):
        yield from base_mcps(
            urn,
            f"loadtest.lineage.downstream_{i:03d}",
            f"Downstream neighbor #{i} of lineage hub",
        )

    # Hub has all upstreams
    yield MetadataChangeProposalWrapper(
        entityUrn=hub_urn,
        aspect=UpstreamLineageClass(
            upstreams=[
                UpstreamClass(dataset=u, type=DatasetLineageTypeClass.TRANSFORMED)
                for u in upstream_urns
            ]
        ),
    )
    # Each downstream points at hub
    for d in downstream_urns:
        yield MetadataChangeProposalWrapper(
            entityUrn=d,
            aspect=UpstreamLineageClass(
                upstreams=[
                    UpstreamClass(dataset=hub_urn, type=DatasetLineageTypeClass.TRANSFORMED)
                ]
            ),
        )


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--gms", default="http://localhost:8080")
    p.add_argument("--frontend", default="http://localhost:9002")
    p.add_argument("--username", default="datahub")
    p.add_argument("--password", default="datahub")
    p.add_argument("--upstream", type=int, default=100)
    p.add_argument("--downstream", type=int, default=100)
    p.add_argument("--batch-size", type=int, default=200)
    p.add_argument("--platform", default="snowflake")
    p.add_argument("--env", default="PROD")
    args = p.parse_args()

    token = login_token(args.frontend, args.username, args.password)
    emitter = DatahubRestEmitter(args.gms, token=token)

    batch: List[MetadataChangeProposalWrapper] = []
    total = 0
    t0 = time.time()
    for mcp in lineage_mcps(args.platform, args.env, args.upstream, args.downstream):
        batch.append(mcp)
        if len(batch) >= args.batch_size:
            emitter.emit_mcps(batch, emit_mode=EmitMode.SYNC_WAIT)
            total += len(batch)
            print(f"emitted {total}", flush=True)
            batch = []
    if batch:
        emitter.emit_mcps(batch, emit_mode=EmitMode.SYNC_WAIT)
        total += len(batch)

    hub = make_dataset_urn(args.platform, "loadtest.lineage.hub", args.env)
    print(
        f"\nDone in {time.time()-t0:.1f}s ({total} MCPs).\n"
        f"Hub URN:\n  {hub}\n"
        f"UI search: loadtest.lineage.hub\n"
        f"Then open Lineage and expand Upstream / Downstream.\n",
        flush=True,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
