"""Reject missing, empty, failed or incomplete Horizon QA client runs."""

import json
import sys
from pathlib import Path


def check_result(result):
    if result["schemaVersion"] != 4:
        raise ValueError("Unsupported Horizon QA report schema")
    if result["exitCode"] != 0 or result["status"] != "passed":
        raise ValueError("Horizon QA did not finish successfully")
    configuration = result["configuration"]
    if configuration["mode"] != "ci" or configuration["tests"] != "galaxia":
        raise ValueError("Expected the complete Galaxia CI suite")
    counts = result["counts"]
    tests = result["tests"]
    selected = counts["selectedTests"]
    if selected <= 0 or counts["passed"] != selected or len(tests) != selected:
        raise ValueError("Not all selected tests completed successfully")
    for key in ("failed", "timedOut", "skipped", "incomplete", "issues", "diagnosticErrors"):
        if counts[key] != 0:
            raise ValueError(f"Horizon QA reported {key}: {counts[key]}")
    if result["issues"] or any(test["status"] != "passed" for test in tests):
        raise ValueError("Horizon QA reported unsuccessful tests or infrastructure issues")
    return selected


if __name__ == "__main__":
    try:
        result = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
        count = check_result(result)
    except (OSError, ValueError, KeyError, TypeError, IndexError) as error:
        print(f"Horizon QA validation failed: {error}", file=sys.stderr)
        sys.exit(1)
    print(f"Horizon QA: {count}/{count} scenarios passed")
