#!/usr/bin/env python3
"""Ingest load-test metadata into a local DataHub GMS.

Creates:
  - ~N searchable Snowflake datasets (default 20000)
  - A column-level lineage showcase: loadtest.cll.source_wide -> loadtest.cll.mart_wide
    with COLUMN_COUNT fine-grained field edges (default 100)

Usage:
  /Users/stan.dmitriev/.local/pipx/venvs/acryl-datahub/bin/python scripts/ingest_loadtest.py
"""

from __future__ import annotations

import argparse
import base64
import json
import sys
import time
import urllib.request
from typing import Iterable, List

from datahub.emitter.mce_builder import make_data_platform_urn, make_dataset_urn, make_schema_field_urn
from datahub.emitter.mcp import MetadataChangeProposalWrapper
from datahub.emitter.rest_emitter import DatahubRestEmitter, EmitMode
from datahub.metadata.schema_classes import (
    AuditStampClass,
    DatasetLineageTypeClass,
    DatasetPropertiesClass,
    FineGrainedLineageClass,
    FineGrainedLineageDownstreamTypeClass,
    FineGrainedLineageUpstreamTypeClass,
    NumberTypeClass,
    OtherSchemaClass,
    SchemaFieldClass,
    SchemaFieldDataTypeClass,
    SchemaMetadataClass,
    StatusClass,
    StringTypeClass,
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
            payload_b64 = val.split(".")[1]
            pad = "=" * ((4 - len(payload_b64) % 4) % 4)
            payload = json.loads(base64.urlsafe_b64decode(payload_b64 + pad))
            return payload["data"]["token"]
    raise RuntimeError("Could not extract token from frontend login")


def field(name: str, number: bool = False) -> SchemaFieldClass:
    dtype = (
        SchemaFieldDataTypeClass(type=NumberTypeClass())
        if number
        else SchemaFieldDataTypeClass(type=StringTypeClass())
    )
    return SchemaFieldClass(
        fieldPath=name,
        type=dtype,
        nativeDataType="BIGINT" if number else "VARCHAR",
        description=f"Loadtest column {name}",
        lastModified=AuditStampClass(
            time=int(time.time() * 1000), actor="urn:li:corpuser:datahub"
        ),
    )


def schema_aspect(platform: str, schema_name: str, columns: List[str]) -> SchemaMetadataClass:
    return SchemaMetadataClass(
        schemaName=schema_name,
        platform=make_data_platform_urn(platform),
        version=0,
        hash="",
        platformSchema=OtherSchemaClass(rawSchema=""),
        fields=[field(c, number=(i % 5 == 0)) for i, c in enumerate(columns)],
    )


def dataset_base_mcps(
    urn: str, name: str, description: str, columns: List[str], platform: str
) -> List[MetadataChangeProposalWrapper]:
    return [
        MetadataChangeProposalWrapper(entityUrn=urn, aspect=StatusClass(removed=False)),
        MetadataChangeProposalWrapper(
            entityUrn=urn,
            aspect=DatasetPropertiesClass(name=name, description=description),
        ),
        MetadataChangeProposalWrapper(
            entityUrn=urn, aspect=schema_aspect(platform, name, columns)
        ),
    ]


def cll_mcps(platform: str, env: str, column_count: int) -> List[MetadataChangeProposalWrapper]:
    cols = [f"col_{i:03d}" for i in range(column_count)]
    up_name = "loadtest.cll.source_wide"
    down_name = "loadtest.cll.mart_wide"
    up_urn = make_dataset_urn(platform, up_name, env)
    down_urn = make_dataset_urn(platform, down_name, env)

    mcps: List[MetadataChangeProposalWrapper] = []
    mcps.extend(
        dataset_base_mcps(
            up_urn,
            up_name,
            f"CLL source with {column_count} columns — open Lineage on mart_wide",
            cols,
            platform,
        )
    )
    mcps.extend(
        dataset_base_mcps(
            down_urn,
            down_name,
            f"CLL mart with {column_count} column-level lineage edges from source_wide",
            cols,
            platform,
        )
    )
    fgl = [
        FineGrainedLineageClass(
            upstreamType=FineGrainedLineageUpstreamTypeClass.FIELD_SET,
            upstreams=[make_schema_field_urn(up_urn, c)],
            downstreamType=FineGrainedLineageDownstreamTypeClass.FIELD,
            downstreams=[make_schema_field_urn(down_urn, c)],
            confidenceScore=0.95,
            transformOperation="identity",
        )
        for c in cols
    ]
    mcps.append(
        MetadataChangeProposalWrapper(
            entityUrn=down_urn,
            aspect=UpstreamLineageClass(
                upstreams=[
                    UpstreamClass(dataset=up_urn, type=DatasetLineageTypeClass.TRANSFORMED)
                ],
                fineGrainedLineages=fgl,
            ),
        )
    )
    return mcps


def search_corpus_mcps(
    platform: str, env: str, count: int, cols_per_table: int
) -> Iterable[MetadataChangeProposalWrapper]:
    cols = [f"f_{i}" for i in range(cols_per_table)]
    for i in range(count):
        # Spread across schemas so browse/search feels realistic
        schema = f"schema_{(i // 200) % 50:02d}"
        name = f"loadtest.search.analytics.{schema}.asset_{i:05d}"
        urn = make_dataset_urn(platform, name, env)
        yield from dataset_base_mcps(
            urn,
            name,
            f"Synthetic searchable asset #{i} in {schema} (loadtest corpus)",
            cols,
            platform,
        )


def emit_batches(
    emitter: DatahubRestEmitter,
    mcps: Iterable[MetadataChangeProposalWrapper],
    batch_size: int,
) -> int:
    batch: List[MetadataChangeProposalWrapper] = []
    total = 0
    for mcp in mcps:
        batch.append(mcp)
        if len(batch) >= batch_size:
            emitter.emit_mcps(batch, emit_mode=EmitMode.ASYNC)
            total += len(batch)
            print(f"emitted {total} MCPs...", flush=True)
            batch = []
    if batch:
        emitter.emit_mcps(batch, emit_mode=EmitMode.ASYNC)
        total += len(batch)
        print(f"emitted {total} MCPs (final)", flush=True)
    return total


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--gms", default="http://localhost:8080")
    p.add_argument("--frontend", default="http://localhost:9002")
    p.add_argument("--username", default="datahub")
    p.add_argument("--password", default="datahub")
    p.add_argument("--assets", type=int, default=20000)
    p.add_argument("--columns", type=int, default=100, help="CLL column count")
    p.add_argument("--cols-per-search-asset", type=int, default=5)
    p.add_argument("--batch-size", type=int, default=200)
    p.add_argument("--platform", default="snowflake")
    p.add_argument("--env", default="PROD")
    args = p.parse_args()

    print("Logging in...", flush=True)
    token = login_token(args.frontend, args.username, args.password)
    emitter = DatahubRestEmitter(args.gms, token=token)

    print(f"Ingesting CLL showcase ({args.columns} columns)...", flush=True)
    cll = cll_mcps(args.platform, args.env, args.columns)
    emit_batches(emitter, cll, args.batch_size)

    print(f"Ingesting search corpus ({args.assets} datasets)...", flush=True)
    total = emit_batches(
        emitter,
        search_corpus_mcps(
            args.platform, args.env, args.assets, args.cols_per_search_asset
        ),
        args.batch_size,
    )

    down_urn = make_dataset_urn(
        args.platform, "loadtest.cll.mart_wide", args.env
    )
    print(
        "\nDone.\n"
        f"  Search for: loadtest.search\n"
        f"  CLL dataset: {down_urn}\n"
        f"  UI lineage: http://localhost:9002/dataset/{urllib_quote(down_urn)}/Lineage?is_lineage_mode=true&column=col_000\n"
        f"  MCPs (search corpus pass): ~{total}\n",
        flush=True,
    )
    return 0


def urllib_quote(s: str) -> str:
    from urllib.parse import quote

    return quote(s, safe="")


if __name__ == "__main__":
    sys.exit(main())
