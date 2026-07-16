# vendor/

`datahub/` is a git submodule. For this workspace it points at the **Aiven fork** so the OpenSearch 3 shim branch is fetchable for team review:

- URL: https://github.com/StanDmitrievAiven/datahub.git  
- Branch: `feat/opensearch-3-shim`  
- Upstream (do not PR from here until review): https://github.com/datahub-project/datahub  

```bash
git submodule update --init --recursive
git -C vendor/datahub log -1 --oneline
```

Path B implementation lives **inside** the submodule. Update `docs/spike/datahub-pin.txt` when you intentionally move the pin.
