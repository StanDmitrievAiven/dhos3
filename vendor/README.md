# vendor/

`datahub/` is added as a git submodule pointing at https://github.com/datahub-project/datahub

```bash
git submodule add https://github.com/datahub-project/datahub.git vendor/datahub
git -C vendor/datahub rev-parse HEAD > docs/spike/datahub-pin.txt
```

Path B implementation happens on a feature branch **inside** the submodule. Update `docs/spike/datahub-pin.txt` when you intentionally move the pin.
