#!/usr/bin/env python3
"""Regression gate for generated FDL field mappings retaining DB field types."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SYNC_TASK = ROOT / "plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/SyncTaskNodeBean.java"


def method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[brace + 1:index]
    raise AssertionError(f"unterminated method: {signature}")


def main() -> None:
    source = SYNC_TASK.read_text(encoding="utf-8")
    body = method_body(source, "public Component getFDLComponent()")

    refresh_call = "DBFieldTransferUtils.refreshWithoutError(result)"
    typed_mapping = "SyncTaskNodeBean.createFieldTransferItems(refresh, this.fieldLineageInfo)"
    offline_fallback = "fieldTransferItems = this.createOfflineFieldTransferItems()"

    for fragment in (refresh_call, typed_mapping, offline_fallback):
        assert fragment in body, f"missing generated mapping step: {fragment}"

    assert body.index(refresh_call) < body.index(typed_mapping), (
        "FDL metadata refresh must run before constructing field-transfer items"
    )
    assert body.index(typed_mapping) < body.index(offline_fallback), (
        "name-only offline mappings must be a fallback, not the primary generated mapping"
    )
    assert "if (fieldTransferItems.isEmpty())" in body, (
        "offline mapping may only be used when runtime metadata refresh yields no mapping"
    )

    typed_factory = method_body(
        source,
        "private static List<FieldTransferBean> createFieldTransferItems(",
    )
    assert "List<SqlFieldBean> origin = refresh.getOrigin()" in typed_factory
    assert "List<SqlFieldBean> target = refresh.getTarget()" in typed_factory
    assert "new FieldTransferBean(originFieldBean, targetFieldBean, false)" in typed_factory

    print("generated field mapping type regression passed")


if __name__ == "__main__":
    main()
