#!/usr/bin/env python3
"""End-to-end semantic search smoke vs local GMS + Aiven OS3 + host Ollama.

Creates a few Document entities, embeds with nomic-embed-text, emits
semanticContent, then runs GraphQL semanticSearchAcrossEntities (and an
optional filtered query for D1 product-path coverage).

Uses the acryl-datahub pipx venv (Document SDK + aspect classes).
"""

from __future__ import annotations

import argparse
import base64
import json
import sys
import time
import urllib.error
import urllib.request
import uuid

# Prefer pipx datahub env when invoked as plain python3
try:
    from datahub.emitter.mcp import MetadataChangeProposalWrapper
    from datahub.emitter.rest_emitter import DatahubRestEmitter
    from datahub.metadata.schema_classes import (
        EmbeddingChunkClass,
        EmbeddingModelDataClass,
        SemanticContentClass,
    )
    from datahub.sdk.document import Document
except ImportError:
    print(
        "Missing datahub SDK. Run with:\n"
        "  /Users/stan.dmitriev/.local/pipx/venvs/acryl-datahub/bin/python "
        "scripts/semantic_os3_e2e.py",
        file=sys.stderr,
    )
    sys.exit(1)

SAMPLE_DOCS = [
    {
        "title": "Getting Started with DataHub",
        "text": (
            "Getting Started with DataHub\n\n"
            "DataHub is a modern data catalog for discovery, lineage, and governance.\n"
            "Use search to find datasets, explore lineage, and document your assets.\n"
        ),
        "sub_type": "guide",
    },
    {
        "title": "Data Access Request Process",
        "text": (
            "Data Access Request Process\n\n"
            "How to request access to data assets in the platform.\n"
            "Submit a request, wait for owner approval, then access is granted.\n"
            "Covers read access for dashboards and write access for datasets.\n"
        ),
        "sub_type": "process",
    },
    {
        "title": "Machine Learning Model: Churn Prediction",
        "text": (
            "Churn Prediction Model\n\n"
            "Predicts probability of customer churn within 30 days.\n"
            "Features include login recency, purchase frequency, support tickets.\n"
            "AUC-ROC 0.87, precision 0.82.\n"
        ),
        "sub_type": "model",
    },
]


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
    raise RuntimeError("login failed")


def graphql(gms: str, token: str, query: str, variables: dict | None = None) -> dict:
    # Hit GMS GraphQL with Bearer (frontend /api/graphql expects session cookie).
    url = f"{gms.rstrip('/')}/api/graphql"
    req = urllib.request.Request(
        url,
        data=json.dumps({"query": query, "variables": variables or {}}).encode(),
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode())


def _aiven_auth_headers() -> tuple[str, dict]:
    """Return (base_url, headers) for Aiven from OS3D .env — never print password."""
    from pathlib import Path

    env_path = Path(__file__).resolve().parents[1] / ".env"
    env: dict[str, str] = {}
    if env_path.exists():
        for line in env_path.read_text().splitlines():
            if line.strip() and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env[k.strip()] = v.strip().strip('"').strip("'")
    import os

    for k, v in env.items():
        os.environ.setdefault(k, v)
    host = os.environ["AIVEN_OPENSEARCH_HOST"]
    port = os.environ.get("AIVEN_OPENSEARCH_PORT", "443")
    user = os.environ["AIVEN_OPENSEARCH_USERNAME"]
    password = os.environ["AIVEN_OPENSEARCH_PASSWORD"]
    tok = base64.b64encode(f"{user}:{password}".encode()).decode()
    return f"https://{host}:{port}", {
        "Authorization": f"Basic {tok}",
        "Content-Type": "application/json",
    }


def _seed_semantic_doc(
    *,
    urn: str,
    title: str,
    text: str,
    subtype: str,
    model_key: str,
    vector: list[float],
) -> None:
    import ssl

    base, headers = _aiven_auth_headers()
    body = {
        "urn": urn,
        "typeNames": [subtype],
        "name": title,
        "embeddings": {
            model_key: {
                "chunks": [
                    {
                        "vector": vector,
                        "text": text[:500],
                        "position": 0,
                        "characterOffset": 0,
                        "characterLength": len(text),
                    }
                ]
            }
        },
    }
    doc_id = urn.replace(":", "_")
    req = urllib.request.Request(
        f"{base}/documentindex_v2_semantic/_doc/{doc_id}",
        data=json.dumps(body).encode(),
        headers=headers,
        method="PUT",
    )
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, context=ctx, timeout=60) as resp:
        print(f"  seeded OS semantic doc HTTP {resp.status}")


def _refresh_semantic_index() -> None:
    import ssl

    base, headers = _aiven_auth_headers()
    req = urllib.request.Request(
        f"{base}/documentindex_v2_semantic/_refresh",
        headers=headers,
        method="POST",
    )
    ctx = ssl.create_default_context()
    urllib.request.urlopen(req, context=ctx, timeout=30)


def embed(endpoint: str, model: str, text: str) -> list[float]:
    req = urllib.request.Request(
        endpoint,
        data=json.dumps({"model": model, "input": text}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = json.loads(resp.read().decode())
    return list(data["data"][0]["embedding"])


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--gms", default="http://localhost:8080")
    p.add_argument("--frontend", default="http://localhost:9002")
    p.add_argument("--username", default="datahub")
    p.add_argument("--password", default="datahub")
    p.add_argument(
        "--ollama-embeddings",
        default="http://localhost:11434/v1/embeddings",
    )
    p.add_argument("--model", default="nomic-embed-text")
    p.add_argument("--wait-seconds", type=int, default=25)
    p.add_argument(
        "--seed-opensearch",
        action="store_true",
        help="Also write embeddings directly to documentindex_v2_semantic "
        "(use when MAE dual-write is stuck behind a large restore backlog).",
    )
    args = p.parse_args()

    model_key = args.model.replace("-", "_").replace(".", "_")
    print(f"model_key={model_key}")

    token = login_token(args.frontend, args.username, args.password)
    print("login ok")

    # AppConfig: semantic enabled + local provider
    cfg = graphql(
        args.gms,
        token,
        """
        query {
          appConfig {
            semanticSearchConfig {
              enabled
              enabledEntities
              embeddingConfig { provider modelEmbeddingKey }
            }
          }
        }
        """,
    )
    if cfg.get("errors"):
        print("appConfig errors:", cfg["errors"], file=sys.stderr)
        return 1
    sem = cfg["data"]["appConfig"]["semanticSearchConfig"]
    print("appConfig.semanticSearchConfig=", json.dumps(sem))
    if not sem or not sem.get("enabled"):
        print("FAIL: semantic search not enabled on GMS", file=sys.stderr)
        return 2

    emitter = DatahubRestEmitter(gms_server=args.gms, token=token)
    created: list[tuple[str, str, str]] = []  # id, urn, title

    for doc in SAMPLE_DOCS:
        doc_id = f"os3-sem-{uuid.uuid4().hex[:8]}"
        d = Document.create_document(
            id=doc_id,
            title=doc["title"],
            text=doc["text"],
            subtype=doc["sub_type"],
        )
        for mcp in d.as_mcps():
            emitter.emit(mcp)
        urn = f"urn:li:document:{doc_id}"
        created.append((doc_id, urn, doc["title"]))
        print(f"created {urn}")

        vec = embed(args.ollama_embeddings, args.model, doc["text"])
        print(f"  embedded dims={len(vec)}")
        chunk = EmbeddingChunkClass(
            position=0,
            vector=vec,
            text=doc["text"][:500],
            characterOffset=0,
            characterLength=len(doc["text"]),
        )
        model_data = EmbeddingModelDataClass(
            modelVersion=f"local/{args.model}",
            generatedAt=int(time.time() * 1000),
            chunkingStrategy="single_chunk_e2e",
            totalChunks=1,
            chunks=[chunk],
        )
        aspect = SemanticContentClass(embeddings={model_key: model_data})
        mcp = MetadataChangeProposalWrapper(entityUrn=urn, aspect=aspect)
        emitter.emit(mcp)
        print(f"  emitted semanticContent key={model_key}")
        if args.seed_opensearch:
            _seed_semantic_doc(
                urn=urn,
                title=doc["title"],
                text=doc["text"],
                subtype=doc["sub_type"],
                model_key=model_key,
                vector=vec,
            )

    emitter.close()
    if args.seed_opensearch:
        _refresh_semantic_index()
        print("seeded + refreshed documentindex_v2_semantic")
    else:
        print(f"waiting {args.wait_seconds}s for MAE / semantic index…")
        time.sleep(args.wait_seconds)

    # Unfiltered semantic search — should prefer access-request doc
    q = """
    query($i: SearchAcrossEntitiesInput!) {
      semanticSearchAcrossEntities(input: $i) {
        total
        searchResults {
          entity { urn ... on Document { info { title } } }
        }
      }
    }
    """
    variables = {
        "i": {
            "query": "how do I request access to a dataset",
            "types": ["DOCUMENT"],
            "start": 0,
            "count": 5,
        }
    }
    try:
        res = graphql(args.gms, token, q, variables)
    except urllib.error.HTTPError as e:
        print(f"semantic search HTTP {e.code}: {e.read()[:500]}", file=sys.stderr)
        return 1

    if res.get("errors"):
        print("semantic search errors:", json.dumps(res["errors"], indent=2))
        return 1

    data = res["data"]["semanticSearchAcrossEntities"]
    print("semanticSearchAcrossEntities total=", data["total"])
    titles = []
    for hit in data["searchResults"]:
        ent = hit["entity"]
        title = (ent.get("info") or {}).get("title")
        titles.append((ent["urn"], title))
        print(f"  hit: {title} | {ent['urn']}")

    our_urns = {u for _, u, _ in created}
    hit_ours = [t for u, t in titles if u in our_urns]
    access_hit = any(
        u in our_urns and t and "Access" in t for u, t in titles
    )

    # Filtered query (platform/type if supported) — exercises D1 product path
    q_filt = """
    query($i: SearchAcrossEntitiesInput!) {
      semanticSearchAcrossEntities(input: $i) {
        total
        searchResults { entity { urn } }
      }
    }
    """
    filt_vars = {
        "i": {
            "query": "customer churn machine learning model",
            "types": ["DOCUMENT"],
            "start": 0,
            "count": 5,
            "orFilters": [
                {
                    "and": [
                        {
                            "field": "typeNames",
                            "values": ["model"],
                            "condition": "EQUAL",
                        }
                    ]
                }
            ],
        }
    }
    filt_res = graphql(args.gms, token, q_filt, filt_vars)
    if filt_res.get("errors"):
        print("filtered semantic WARN errors:", filt_res["errors"])
        filt_total = None
    else:
        filt_total = filt_res["data"]["semanticSearchAcrossEntities"]["total"]
        print("filtered semantic (typeNames=model) total=", filt_total)
        for hit in filt_res["data"]["semanticSearchAcrossEntities"]["searchResults"]:
            print(f"  filtered hit: {hit['entity']['urn']}")

    print("---")
    if data["total"] > 0 and hit_ours:
        print("VERDICT: SEMANTIC_SEARCH_PASS")
        if access_hit:
            print("relevance: access-request doc in top hits (good)")
        else:
            print("relevance: our docs returned but access doc not top — soft warn")
        if filt_total == 0:
            print(
                "filtered: 0 hits — possible D1 / filter-shape issue on product path "
                "(or filter field unsupported); investigate GMS logs / builder"
            )
        elif filt_total is not None:
            print("filtered: non-zero — filter path OK")
        return 0

    print("VERDICT: SEMANTIC_SEARCH_FAIL", file=sys.stderr)
    print("created urns:", [u for _, u, _ in created], file=sys.stderr)
    return 3


if __name__ == "__main__":
    sys.exit(main())
