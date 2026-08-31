#!/usr/bin/env python3
"""Static companion gate for the executable JDK8 field-mapping probe."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083"


def main() -> None:
    sync = (SRC / "bean/parse/core/fdl/SyncTaskNodeBean.java").read_text(encoding="utf-8")
    transform = (SRC / "bean/parse/core/fdl/TransformSyncTaskNodeBean.java").read_text(encoding="utf-8")
    api = (SRC / "bean/parse/core/fdl/ApiSyncTaskNodeBean.java").read_text(encoding="utf-8")
    factory = (SRC / "bean/parse/core/fdl/FieldTransferFactory.java").read_text(encoding="utf-8")
    parser = (SRC / "util/xml/DataSyncParser.java").read_text(encoding="utf-8")
    normalizer = (SRC / "util/xml/InfaSqlProjectionNormalizer.java").read_text(encoding="utf-8")

    assert "FieldTransferFactory.createForSync" in sync
    assert "createOfflineFieldTransferItems" not in sync
    assert "FieldTransferFactory.createForTransform" in transform
    assert "FieldTransferFactory.createForTransform" in api
    assert "FieldTransferFactory.createTargetField" in api
    assert "new FieldTransferBean(readColumn, writeColumn, false)" in factory
    assert "typedXmlField(binding.getReadMeta()" in factory
    assert "targetField(binding.getWriteMeta()" in factory
    assert "runtimeTarget.getType() != 0" in factory
    assert "return copy(runtimeTarget, name)" in factory
    assert "runtimeFallback.getType() == 0" in factory
    assert "InfaSqlProjectionNormalizer.normalize" in parser
    assert "edgeWhitespace(originalProjection, true)" in normalizer
    assert "edgeWhitespace(originalProjection, false)" in normalizer
    classifier = parser.split("private static boolean isExternalApiTransformation", 1)[1].split(
        "private static ApiNodeFromInfoBean createApiConfigFromTransformation", 1
    )[0]
    assert '"Lookup Procedure".equalsIgnoreCase(type)' in classifier
    assert 'lower.contains("procedure")' not in classifier

    print("PASS generated field mapping type regression passed; run scripts/test_infa_field_mapping_types.sh for DTO assertions")


if __name__ == "__main__":
    main()
