"""Diagnostics orchestration only: no ADB calls or production behavior."""
from pathlib import Path
import sys
from types import SimpleNamespace
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools'))
from run_user_transition import select_for_user, plan, check_capture, cleanup


def test_selection_is_keyed_by_user_not_list_position():
    a, b = dict(user=0, fixture='a'), dict(user=17, fixture='b')
    assert select_for_user([b, a], 0) is a
    assert select_for_user([b, a], 17) is b


@pytest.mark.parametrize('selections', [[], [dict(user=17), dict(user=17)]])
def test_missing_or_duplicate_user_selection_is_rejected(selections):
    with pytest.raises(ValueError, match='AMBIGUOUS_OR_MISSING'):
        select_for_user(selections, 17)


def test_exact_five_phase_order():
    assert plan(17) == [('owner-initial', 0, False), ('secondary-warm', 17, False),
                        ('secondary-cold', 17, True), ('owner-return-warm', 0, False),
                        ('owner-return-cold', 0, True)]


@pytest.mark.parametrize('user', [0, -1, True, '17'])
def test_invalid_secondary_rejected(user):
    with pytest.raises(ValueError):
        plan(user)


def evidence(cold=False):
    item = dict(user=17, fixture='b', sha256='b'*64)
    report = dict(android_user=17, uid=1701234, fixture='b', profile_sha256='b'*64,
                  cold_restart_requested=cold, process_reused=not cold, request_id='distinct', pid=123)
    return item, report


@pytest.mark.parametrize('cold', [False, True])
def test_warm_cold_metadata_requires_observed_process_lifecycle(cold):
    item, report = evidence(cold)
    assert check_capture(report, item, 'b'*64, cold, require_warm=not cold)['match']
    report['process_reused'] = cold
    with pytest.raises(ValueError):
        check_capture(report, item, 'b'*64, cold, require_warm=not cold)


@pytest.mark.parametrize('field,value', [('android_user', 0), ('uid', 1234),
    ('fixture', 'a'), ('profile_sha256', 'a'*64), ('cold_restart_requested', True)])
def test_cross_user_fixture_hash_or_mode_mismatch_rejected(field, value):
    item, report = evidence()
    report[field] = value
    with pytest.raises(ValueError):
        check_capture(report, item, 'b'*64, False, True)


def test_wrong_published_hash_rejected():
    item, report = evidence()
    with pytest.raises(ValueError, match='HASH_MISMATCH'):
        check_capture(report, item, 'a'*64, False, True)


@pytest.mark.parametrize('active', [False, True])
def test_cleanup_covers_both_users_even_if_last_selection_was_secondary(active):
    selections = [dict(user=0, fixture='a'), dict(user=17, fixture='b')]
    calls, commands = [], []
    h = SimpleNamespace(m=dict(overlay_active=active, selections=[selections[1]]),
                        d=SimpleNamespace(shell=lambda cmd, **kw: commands.append(cmd)))
    h.overlay = lambda remove: calls.append((remove, list(h.m['selections']))) or dict(removed=True)
    cleanup(h, selections, [0, 17], 'dev.cyclone.cloak.probe.diag')
    assert calls == ([(True, selections)] if active else [])
    assert len(commands) == 4
    assert all('dev.cyclone.cloak.probe.diag' in c for c in commands)
    assert sum('rm -f files/expected-profile.json files/report.json' in c for c in commands) == 2
