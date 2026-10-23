package dev.cyclone.cloak.probe;

import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded stack structure, never exception text, filenames or source contents. */
final class ProbeFailureEvidence {
    private ProbeFailureEvidence() { }
    static JSONObject describe(Throwable error, String rowId) throws Exception {
        JSONObject result = new JSONObject().put("exception_class", error.getClass().getName())
            .put("exception_message", "REDACTED_EXCEPTION_MESSAGE").put("message_redacted", true)
            .put("failed_row_id", rowId == null ? "capture.unknown" : rowId);
        JSONArray stack = new JSONArray();
        StackTraceElement[] frames = error.getStackTrace();
        for (int i = 0; i < Math.min(32, frames.length); i++) {
            StackTraceElement frame = frames[i];
            stack.put(new JSONObject().put("class", frame.getClassName()).put("method", frame.getMethodName())
                .put("line", frame.getLineNumber()));
        }
        return result.put("stack_trace", stack).put("stack_truncated", frames.length > 32);
    }
}
