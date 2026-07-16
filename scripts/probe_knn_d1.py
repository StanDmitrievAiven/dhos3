#!/usr/bin/env python3
"""Re-verify spike D1 on Aiven OS3: in-knn nested filter vs bool+nested filter.

Creates a temporary index, runs three queries, deletes the index.
Credentials from .env (AIVEN_OPENSEARCH_*). Never prints password.
"""

from __future__ import annotations

import json
import os
import ssl
import sys
import urllib.error
import urllib.request
from pathlib import Path


def load_env() -> None:
    env_path = Path(__file__).resolve().parents[1] / ".env"
    if not env_path.exists():
        return
    for line in env_path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        os.environ.setdefault(k.strip(), v.strip().strip('"').strip("'"))


def req(method: str, url: str, user: str, password: str, body: dict | None = None):
    data = None if body is None else json.dumps(body).encode()
    r = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    # basic auth
    import base64

    token = base64.b64encode(f"{user}:{password}".encode()).decode()
    r.add_header("Authorization", f"Basic {token}")
    ctx = ssl.create_default_context()
    # Aiven uses public CA; keep verify on. If local self-signed, set AIVEN_INSECURE=1
    if os.environ.get("AIVEN_INSECURE") == "1":
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
    try:
        with urllib.request.urlopen(r, context=ctx, timeout=60) as resp:
            raw = resp.read().decode()
            return resp.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"error": raw[:500]}


def main() -> int:
    load_env()
    host = os.environ["AIVEN_OPENSEARCH_HOST"]
    port = os.environ.get("AIVEN_OPENSEARCH_PORT", "443")
    user = os.environ["AIVEN_OPENSEARCH_USERNAME"]
    password = os.environ["AIVEN_OPENSEARCH_PASSWORD"]
    base = f"https://{host}:{port}"
    index = "dhos3-d1-probe"

    mapping = {
        "settings": {"index": {"knn": True, "number_of_shards": 1, "number_of_replicas": 0}},
        "mappings": {
            "properties": {
                "urn": {"type": "keyword"},
                "embeddings": {
                    "properties": {
                        "demo": {
                            "properties": {
                                "chunks": {
                                    "type": "nested",
                                    "properties": {
                                        "vector": {
                                            "type": "knn_vector",
                                            "dimension": 2,
                                            "method": {
                                                "name": "hnsw",
                                                "space_type": "l2",
                                                "engine": "faiss",
                                                "parameters": {"ef_construction": 64, "m": 16},
                                            },
                                        },
                                        "tag": {"type": "keyword"},
                                    },
                                }
                            }
                        }
                    }
                },
            }
        },
    }

    # cleanup leftover
    req("DELETE", f"{base}/{index}", user, password)
    code, _ = req("PUT", f"{base}/{index}", user, password, mapping)
    if code not in (200, 201):
        print(f"FAIL create index HTTP {code}", file=sys.stderr)
        return 1

    docs = [
        (
            "1",
            {
                "urn": "a",
                "embeddings": {
                    "demo": {"chunks": [{"vector": [1.0, 0.0], "tag": "keep"}]}
                },
            },
        ),
        (
            "2",
            {
                "urn": "b",
                "embeddings": {
                    "demo": {"chunks": [{"vector": [0.0, 1.0], "tag": "drop"}]}
                },
            },
        ),
    ]
    for _id, doc in docs:
        req("PUT", f"{base}/{index}/_doc/{_id}", user, password, doc)
    req("POST", f"{base}/{index}/_refresh", user, password)

    unfiltered = {
        "size": 2,
        "query": {
            "nested": {
                "path": "embeddings.demo.chunks",
                "score_mode": "max",
                "query": {
                    "knn": {
                        "embeddings.demo.chunks.vector": {
                            "vector": [1.0, 0.1],
                            "k": 2,
                        }
                    }
                },
            }
        },
    }
    # DataHub OpenSearch2KnnQueryBuilder shape: filter inside knn params
    in_knn_filter = {
        "size": 2,
        "query": {
            "nested": {
                "path": "embeddings.demo.chunks",
                "score_mode": "max",
                "query": {
                    "knn": {
                        "embeddings.demo.chunks.vector": {
                            "vector": [1.0, 0.1],
                            "k": 2,
                            "filter": {"term": {"embeddings.demo.chunks.tag": "keep"}},
                        }
                    }
                },
            }
        },
    }
    # Workaround: bool must + filter wrapping nested knn
    bool_filter = {
        "size": 2,
        "query": {
            "bool": {
                "must": [
                    {
                        "nested": {
                            "path": "embeddings.demo.chunks",
                            "score_mode": "max",
                            "query": {
                                "knn": {
                                    "embeddings.demo.chunks.vector": {
                                        "vector": [1.0, 0.1],
                                        "k": 2,
                                    }
                                }
                            },
                        }
                    }
                ],
                "filter": [
                    {
                        "nested": {
                            "path": "embeddings.demo.chunks",
                            "query": {
                                "term": {"embeddings.demo.chunks.tag": "keep"}
                            },
                        }
                    }
                ],
            }
        },
    }

    results = {}
    for name, body in [
        ("unfiltered", unfiltered),
        ("in_knn_filter", in_knn_filter),
        ("bool_filter", bool_filter),
    ]:
        code, resp = req("POST", f"{base}/{index}/_search", user, password, body)
        hits = resp.get("hits", {}).get("hits", []) if isinstance(resp, dict) else []
        total = resp.get("hits", {}).get("total", {})
        if isinstance(total, dict):
            total_n = total.get("value", len(hits))
        else:
            total_n = total
        urns = [h.get("_source", {}).get("urn") for h in hits]
        results[name] = {"http": code, "total": total_n, "urns": urns}
        print(f"{name}: http={code} total={total_n} urns={urns}")

    req("DELETE", f"{base}/{index}", user, password)

    # Verdict matching spike D1
    ok_unf = results["unfiltered"]["total"] >= 1
    d1_empty = results["in_knn_filter"]["total"] == 0
    ok_bool = results["bool_filter"]["total"] >= 1 and "a" in results["bool_filter"]["urns"]
    if ok_unf and d1_empty and ok_bool:
        print("VERDICT: D1 CONFIRMED (in-knn filter empty; bool+filter works)")
        return 0
    if ok_unf and not d1_empty and ok_bool:
        print("VERDICT: D1 FIXED/ABSENT (in-knn filter returns hits)")
        return 0
    print("VERDICT: UNEXPECTED", results)
    return 2


if __name__ == "__main__":
    sys.exit(main())
