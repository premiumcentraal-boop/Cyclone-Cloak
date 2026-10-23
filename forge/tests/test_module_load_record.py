"""Host integration guards for ownership and ordering of private companion IPC.

The real socket/write path is exercised by the separately authorized device gate.
These guards prevent moving persistence back into the SELinux-restricted caller.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def test_companion_owns_persistence_and_acknowledges_after_write():
    source = (ROOT / "android/module/src/main/cpp/cloak.cpp").read_text()
    handler = source.split("void companion_handler", 1)[1].split("bool load_profile_for", 1)[0]
    record_branch = handler.split("if (request.magic == kLoadRecordRequestMagic)", 1)[1]
    record_branch = record_branch.split("if (request.magic != kStateRequestMagic)", 1)[0]
    assert record_branch.index("read_exact(socket, payload.data()") < record_branch.index("cloak_load_evidence::persist")
    assert record_branch.index("read_profile_state(user, pkg)") < record_branch.index("cloak_load_evidence::persist")
    assert record_branch.index("cloak_load_evidence::persist") < record_branch.index("write_exact(socket, &reply")
    assert "size <= cloak_load_evidence::kMaxRecord" in record_branch


def test_pre_specialization_only_assembles_and_sends_without_changing_activation():
    source = (ROOT / "android/module/src/main/cpp/cloak.cpp").read_text()
    pre = source.split("void preAppSpecialize", 1)[1].split("void postAppSpecialize", 1)[0]
    evidence = pre.split("g_active = load_profile_for", 1)[1]
    assert "cloak_load_evidence::assemble" in evidence
    assert "send_load_record" in evidence
    assert "cloak_load_evidence::append" not in evidence
    assert "cloak_load_evidence::persist" not in evidence
    assert evidence.count("g_active =") == 0
    sender = source.split("Result send_load_record", 1)[1].split("bool load_profile_for", 1)[0]
    assert "api->connectCompanion()" in sender
    assert "read_exact(socket, &reply" in sender
    assert "SO_RCVTIMEO" in sender and "SO_SNDTIMEO" in sender


def test_root_writer_keeps_fixed_destination_validation_and_size_lock():
    header = (ROOT / "android/module/src/main/cpp/module_load_record.hpp").read_text()
    persist = header.split("inline Result persist", 1)[1]
    assert persist.index("payload.size() > kMaxRecord") < persist.index("json::parse")
    assert persist.index("value.value(\"module_artifact_sha256\"") < persist.index("return append(")
    assert "value.size() != 10" in persist
    assert "value.value(\"target_package\", \"\") != package" in persist
    assert '"load-records.jsonl"' in header
    assert "flock(fd, LOCK_EX)" in header and "O_APPEND" in header
    assert "fdatasync(fd)" in header and "64 * 1024" in header
    assert "unlink(" not in header
