"""Pocket Agent's on-device Python runner (Chaquopy).

Executes agent-written code with stdout/stderr captured and returns a JSON
string: {"ok": bool, "stdout": str, "stderr": str}. The Kotlin side parses
it and feeds the result back to the model so it can debug its own code.
"""

import io
import json
import sys
import traceback


def run(code):
    out = io.StringIO()
    err = io.StringIO()
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout, sys.stderr = out, err
    try:
        exec(compile(code, "<agent>", "exec"), {"__name__": "__main__"})
        return json.dumps(
            {"ok": True, "stdout": out.getvalue(), "stderr": err.getvalue()}
        )
    except Exception:
        return json.dumps(
            {
                "ok": False,
                "stdout": out.getvalue(),
                "stderr": traceback.format_exc(),
            }
        )
    finally:
        sys.stdout, sys.stderr = old_out, old_err
