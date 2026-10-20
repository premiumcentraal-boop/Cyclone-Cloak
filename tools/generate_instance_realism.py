"""Build reviewed instance recipes and the Kotlin mirror. No downloads or host reads."""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'forge'))
from cloak_forge.device_factory import DeviceFactory, InstanceRequest, _body  # noqa: E402
from cloak_forge.forge import forge_profile  # noqa: E402
from cloak_forge.instance_realism import anchor, digest  # noqa: E402
from cloak_forge.reality import at  # noqa: E402
from cloak_forge.templates import TEMPLATES  # noqa: E402


def build():
    allowed = ['reality.memory.values.ram_gib', 'reality.memory.values.storage_gib',
               'reality.memory.source', 'reality.memory.reason', 'telephony', 'locale', 'network']
    allowed += ['device.' + p for p in ('fingerprint', 'version_release', 'sdk_int', 'security_patch',
        'build_id', 'version_incremental', 'build_date_utc', 'bootloader', 'baseband')]
    allowed += ['camera.cameras']
    allowed += ['reality.' + s + '.' + key for s in ('carrier', 'environment', 'cameras', 'sensors', 'packages', 'webview')
                for key in ('availability', 'source', 'reason', 'values')]
    result = {'format': 1, 'allowed_paths': sorted(allowed), 'evidence': {
        'pixel-skus': {'url': 'https://support.google.com/pixelphone/answer/7158570?hl=en',
                       'observed_on': '2026-10-20', 'confidence': 'public reference',
                       'note': 'Reviewed US Pixel SKU subsets from manufacturer specifications. Existing *_gib nominal-capacity catalog convention retained.'},
        'local-configuration': {'url': 'local:reviewed-us-timezone-preferences-v1',
                       'observed_on': '2026-10-20', 'confidence': 'derived',
                       'note': 'Explicit user-configurable US timezone preferences; not carrier, radio or location observations.'},
        'deterministic-derivation-v1': {'url': 'local:existing-identifier-derivation',
                       'observed_on': '2026-10-20', 'confidence': 'derived',
                       'note': 'Configured local identifiers only; no issuance, DRM provisioning or platform claims.'}}, 'sources': {}}
    factory = DeviceFactory()
    # Finite complete RAM/storage pairs. No RAM/storage cross product or guessed SKU.
    supported = {'pixel_6': (8, [128, 256]), 'pixel_6_pro': (12, [128, 256, 512]),
                 'pixel_7': (8, [128, 256]), 'pixel_7_pro': (12, [128, 256, 512]),
                 'pixel_8': (8, [128, 256]), 'pixel_8a': (8, [128, 256]),
                 'pixel_8_pro': (12, [128, 256, 512])}
    result['evidence']['pixel-skus']['supported_us_pairs'] = {k: [[ram, storage] for storage in sizes]
        for k, (ram, sizes) in supported.items()}
    for kind, file in [('factory', 'device-factory-v1.json'), ('phones', 'phones.json')]:
        result['evidence']['catalog-' + kind] = {'url': 'local:catalog/' + file,
            'observed_on': '2026-10-20', 'confidence': 'approximated',
            'revision': digest(json.loads((ROOT / 'catalog' / file).read_text(encoding='utf8'))),
            'note': 'Inherited catalog facts retain their original availability/provenance; firmware/HAL descriptions are not device evidence.'}
    for template in factory.catalog():
        template_id = template['id']
        for kind in ('factory', 'phones'):
            if kind == 'phones' and template_id not in TEMPLATES:
                continue
            base = (_body(template, InstanceRequest(template_id, 'recipe-anchor')) if kind == 'factory'
                    else forge_profile('Recipe anchor', 'ab' * 32, template_id, '0.4'))
            peer = (forge_profile('Recipe anchor', 'ab' * 32, template_id, '0.4') if kind == 'factory' and template_id in TEMPLATES
                    else _body(template, InstanceRequest(template_id, 'recipe-anchor')))
            firmware_fields = ['device.' + key for key in ('fingerprint', 'version_release', 'sdk_int', 'security_patch',
                'build_id', 'version_incremental', 'build_date_utc', 'bootloader', 'baseband')]
            same_hardware = all(base['device'].get(k) == peer['device'].get(k) for k in
                ('manufacturer', 'brand', 'model', 'product', 'device', 'hardware', 'first_api_level'))
            peer_available = same_hardware and all(at(peer, p) is not None for p in firmware_fields) and any(
                at(base, p) != at(peer, p) for p in firmware_fields)
            memory = at(base, 'reality.memory.values') or {}
            known_memory = at(base, 'reality.memory.availability') == 'known'
            skus = [(memory.get('ram_gib'), memory.get('storage_gib'))]
            if template_id in supported and known_memory and base['locale']['country'] == 'US':
                ram, sizes = supported[template_id]
                skus = [(ram, storage) for storage in sizes]
            zones = [base['locale']['timezone']]
            if base['locale']['country'] == 'US':
                zones = ['America/New_York', 'America/Los_Angeles']
            tuples = []
            for ram, storage in skus:
                for zone in zones:
                    changes = {}
                    def fact(path, value, source):
                        if at(base, path) != value:
                            changes[path] = {'value': value, 'source': source,
                                'confidence': result['evidence'][source]['confidence'], 'availability': 'known'}
                    if template_id in supported and known_memory and base['locale']['country'] == 'US':
                        fact('reality.memory.values.ram_gib', ram, 'pixel-skus')
                        fact('reality.memory.values.storage_gib', storage, 'pixel-skus')
                        if changes:
                            fact('reality.memory.source', 'public reference', 'pixel-skus')
                            fact('reality.memory.reason', 'Reviewed US nominal SKU; public specifications. Not a capacity measurement.', 'pixel-skus')
                    if zone != base['locale']['timezone']:
                        fact('locale', {**base['locale'], 'timezone': zone}, 'local-configuration')
                        if at(base, 'reality.environment.availability') == 'known':
                            fact('reality.environment.values', {**at(base, 'reality.environment.values'), 'timezone': zone}, 'local-configuration')
                            fact('reality.environment.source', 'derived', 'local-configuration')
                            fact('reality.environment.reason', 'Local configured timezone preference; no location observation.', 'local-configuration')
                    tuple_id = f'sku-{ram or "unknown"}-{storage or "unknown"}-' + zone.rsplit('/', 1)[-1].lower()
                    tuples.append({'id': tuple_id, 'changes': changes})
                    if peer_available:
                        peer_source = 'catalog-phones' if kind == 'factory' else 'catalog-factory'
                        combined = dict(changes)
                        for path in firmware_fields:
                            value = at(peer, path)
                            combined[path] = {'value': value, 'source': peer_source, 'confidence': 'approximated',
                                'availability': 'unknown' if value == 'unknown' else 'known'}
                        tuples.append({'id': tuple_id + '-firmware-peer', 'changes': combined})
            result['sources'][kind + '.' + template_id] = {'template_id': template_id, 'evidence': 'catalog-' + kind,
                'iccid_prefix': template['classes']['carrier_region']['iccid_prefix'] if kind == 'factory' else None,
                'anchor': anchor(base), 'originals': {p: at(base, p) for p in sorted(allowed)},
                'axes': {k: v for k, v in [('memory', 'approved-skus' if len(skus) > 1 else 'fixed-or-unknown'),
                    ('region_locale', 'configured-preferences' if len(zones) > 1 else 'fixed'),
                    ('firmware', 'catalog-bound-alternatives' if peer_available else 'fixed'),
                    ('modem', 'catalog-bound-alternatives' if peer_available else 'fixed'), ('radio_labels', 'derived'),
                    ('identifiers', 'derived'), ('camera_sensor', 'fixed-or-unknown'), ('software_packages', 'fixed-or-unknown')]},
                'tuples': sorted(tuples, key=lambda t: t['id'])}
    return result


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--check', action='store_true'); args = parser.parse_args()
    data = build()
    text = json.dumps(data, indent=2, ensure_ascii=False) + '\n'
    compact = json.dumps(data, ensure_ascii=False, separators=(',', ':'))
    # Chunks stay below the JVM constant-pool string size limit.
    chunks = [compact[i:i+6000] for i in range(0, len(compact), 6000)]
    kotlin = ('package dev.cyclone.cloak.forge\n\n// Generated by tools/generate_instance_realism.py; do not edit.\n'
              'internal object InstanceRealismRules {\n    val json = listOf(\n' +
              ',\n'.join('        ' + json.dumps(c, ensure_ascii=True).replace('$', '\\$') for c in chunks) +
              '\n    ).joinToString("")\n}\n')
    outputs = {ROOT / 'catalog/instance-realism-v1.json': text,
        ROOT / 'android/app/src/main/java/dev/cyclone/cloak/forge/InstanceRealismRules.kt': kotlin}
    for path, content in outputs.items():
        if args.check:
            if not path.exists() or path.read_text(encoding='utf8') != content:
                raise SystemExit('Instance realism generated data is stale')
        else:
            path.write_text(content, encoding='utf8')


if __name__ == '__main__':
    main()
