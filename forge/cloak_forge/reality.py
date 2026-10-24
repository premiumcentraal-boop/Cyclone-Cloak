"""Device Reality v1: local descriptive metadata, never runtime or attestation state."""
from __future__ import annotations

import copy
import hashlib
import json
import math
import re
from pathlib import Path

RULES = json.loads((Path(__file__).resolve().parents[2] / 'schema/device-reality.schema.json').read_text())
SECTIONS = tuple(k for k in RULES['properties'] if k != 'inventory_id')
UNIQUE_PATHS = ('reality.inventory_id', 'reality.radio.values.wifi_name', 'reality.radio.values.bluetooth_name')
LINKS = {
    'panel.width': 'display.width', 'panel.height': 'display.height',
    'panel.density_dpi': 'display.density', 'panel.refresh_hz': 'display.refresh_rate_hz',
    'carrier.slots': 'telephony.sim_slot_count', 'carrier.mcc': 'telephony.mcc', 'carrier.mnc': 'telephony.mnc',
    'environment.language': 'locale.language', 'environment.country': 'locale.country',
    'environment.timezone': 'locale.timezone', 'webview.user_agent': 'ua.value',
}


def at(value, path):
    for key in path.split('.'):
        if not isinstance(value, dict): return None
        value = value.get(key)
    return value


def canonical(value):
    if isinstance(value, dict): return {k: canonical(v) for k,v in value.items()}
    if isinstance(value, list): return [canonical(v) for v in value]
    if type(value) is float and math.isfinite(value) and value.is_integer(): return int(value)
    return value


def token(seed, field):
    return hashlib.sha256(f'cloak-reality-v1:{seed}:{field}'.encode('utf8')).hexdigest()


def generate(template, seed, ua):
    reality = copy.deepcopy(template.get('reality', {}))
    reality['inventory_id'] = token(seed, 'inventory')
    if at(reality,'radio.availability') != 'unavailable':
        radio = copy.deepcopy(at(reality,'radio.values') or {})
        if isinstance(radio,dict):
            radio.update(wifi_name='Cloak-' + token(seed, 'wifi')[:12], bluetooth_name='Cloak-' + token(seed, 'bluetooth')[:12])
            reality['radio'] = dict(availability='known', source='derived',
                reason='Local configured names only; no radio state or broadcast is changed.', values=radio)
    if at(reality,'webview.availability') != 'unavailable':
        web = copy.deepcopy(at(reality,'webview.values') or {})
        if isinstance(web,dict):
            web['user_agent']=ua
            reality['webview'] = dict(availability='known', source='derived',
                reason='User agent derived from this profile; installed provider/version remains unknown.' if 'version' not in web and 'provider' not in web
                    else 'Provider/version retained from template; user agent derived from profile.', values=web)
    packages = _packages(template, reality)
    if packages: reality['packages'] = packages
    return reality


def _webview_version_code(version):
    """Chromium release-code scheme: build*100000 + patch*100 + branch."""
    parts = version.split('.')
    try:
        build, patch, branch = int(parts[2]), int(parts[3]), int(parts[1])
    except (IndexError, ValueError):
        return None
    return build * 100000 + patch * 100 + branch


def _packages(template, reality):
    """Only package versions this template can prove; everything else stays host-read."""
    release = at(template, 'device.version_release')
    sdk = at(template, 'device.sdk_int')
    installed = []
    if isinstance(release, str) and type(sdk) is int:
        installed.append({'package': 'android', 'version_name': release, 'version_code': sdk})
    if at(reality, 'webview.availability') == 'known':
        provider = at(reality, 'webview.values.provider')
        version = at(reality, 'webview.values.version')
        code = _webview_version_code(version) if isinstance(version, str) else None
        if isinstance(provider, str) and isinstance(version, str) and code is not None:
            installed.append({'package': provider, 'version_name': version, 'version_code': code})
    if not installed: return None
    return dict(availability='known', source='derived',
        reason='Framework and WebView versions derived from this profile; other packages remain host-read.',
        values={'installed': installed})


def user_agent(reality, ua):
    version=at(reality,'webview.values.version')
    if at(reality,'webview.availability')=='known' and isinstance(version,str) and re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+',version):
        return re.sub(r'Chrome/[0-9.]+', 'Chrome/'+version, ua)
    return ua


def validate(profile):
    """Fixed reason codes/messages. Never puts the rejected value into a finding."""
    if 'reality' not in profile: return []
    issues = []
    def add(code, path): issues.append(('error', code, path, path + ': ' + MESSAGES[code]))
    if profile.get('schema_version') != '0.4': add('REALITY_SCHEMA', 'reality')
    def check(value, rule, path):
        kind = rule['type']
        ok = {'object': isinstance(value, dict), 'array': isinstance(value, list),
              'string': isinstance(value, str), 'integer': type(value) is int,
              'number': (type(value) is int or type(value) is float and math.isfinite(value)), 'boolean': type(value) is bool}[kind]
        if not ok: add('REALITY_TYPE', path); return
        if 'enum' in rule and value not in rule['enum']: add('REALITY_ENUM', path)
        if kind == 'object':
            for key in sorted(set(value) - set(rule['properties'])): add('REALITY_FIELD', path) # no untrusted key text
            for key in rule.get('required', []):
                if key not in value: add('REALITY_REQUIRED', path + '.' + key)
            for key in sorted(rule['properties']):
                if key in value: check(value[key], rule['properties'][key], path + '.' + key)
        elif kind == 'array':
            if len(value) > rule['maxItems']: add('REALITY_RANGE', path)
            if rule.get('uniqueItems') and len({json.dumps(canonical(v),sort_keys=True) for v in value}) != len(value): add('REALITY_DUPLICATE', path)
            for i,item in enumerate(value[:rule['maxItems']]): check(item,rule['items'],f'{path}[{i}]')
        elif kind == 'string':
            if not rule.get('minLength',0) <= len(value) <= rule.get('maxLength',100000): add('REALITY_RANGE',path)
            if 'pattern' in rule and not re.fullmatch(rule['pattern'], value): add('REALITY_FORMAT',path)
        elif kind in ('integer','number'):
            if not rule['minimum'] <= value <= rule['maximum']: add('REALITY_RANGE',path)
    check(profile['reality'], RULES, 'reality')
    if issues: return issues
    reality=profile['reality']
    def values(section): return reality.get(section,{}).get('values',{})
    def issue(code, section): add(code,'reality.'+section)
    for section in SECTIONS:
        if section not in reality: continue
        entry=reality[section]
        if (entry['availability']=='known') != bool(entry['values']): issue('REALITY_AVAILABILITY',section)
    for link,target in LINKS.items():
        section,key=link.split('.')
        value=values(section).get(key)
        if value is not None and value != at(profile,target): issue('REALITY_LINK',section+'.values.'+key)
    compute=values('compute'); soc=compute.get('soc',''); hardware=at(profile,'device.hardware'); sdk=at(profile,'device.sdk_int'); sdk=sdk if type(sdk) is int else 0
    if soc=='Google Tensor G2' and (hardware!='gs201' or sdk<33): issue('REALITY_SOC','compute')
    if soc=='Snapdragon 8 Gen 2 for Galaxy' and (hardware not in ('qcom','sm8550') or sdk<33): issue('REALITY_SOC','compute')
    if (soc.startswith('Google Tensor') and str(at(profile,'device.manufacturer')).lower()!='google') or (soc=='Snapdragon 8 Gen 2 for Galaxy' and str(at(profile,'device.manufacturer')).lower()!='samsung'): issue('REALITY_SOC','compute')
    gpu=compute.get('gpu','')
    if gpu and ((soc.startswith('Google Tensor') and not gpu.startswith('Mali')) or (soc.startswith('Snapdragon') and not gpu.startswith('Adreno'))): issue('REALITY_SOC','compute')
    memory=values('memory'); storage=memory.get('storage_gib')
    if storage is not None and memory.get('free_storage_gib',0)>storage: issue('REALITY_STORAGE','memory')
    if at(profile,'device.model')=='Pixel 7' and (memory.get('ram_gib',8)!=8 or memory.get('storage_gib',128) not in (128,256)): issue('REALITY_STORAGE','memory')
    power=values('power')
    if at(profile,'device.model')=='Pixel 7' and power.get('design_capacity_mah',4355)!=4355: issue('REALITY_POWER','power')
    if (power.get('battery_state')=='full' and power.get('battery_percent',100)!=100) or (power.get('battery_state')=='charging' and power.get('charging_source')=='none'): issue('REALITY_POWER','power')
    panel=values('panel'); modes=panel.get('modes')
    if modes is not None and not any(all(m.get(k)==panel.get(k) for k in ('width','height','refresh_hz')) for m in modes): issue('REALITY_MODE','panel')
    sensors=values('sensors').get('inventory',[])
    if any((s.get('max_delay_us',0)>0 and s.get('min_delay_us',0)>s['max_delay_us']) or s.get('resolution',0)>s.get('range',1000000) for s in sensors): issue('REALITY_SENSOR','sensors')
    cameras=values('cameras'); inventory=cameras.get('inventory')
    if inventory is not None and (cameras.get('count',len(inventory))!=len(inventory) or len({v['id'] for v in inventory})!=len(inventory)): issue('REALITY_CAMERA','cameras')
    base=at(profile,'camera.cameras')
    if isinstance(base,list) and cameras.get('count',len(base))!=len(base): issue('REALITY_CAMERA','cameras')
    if isinstance(base,list) and inventory is not None:
        names={0:'front',1:'back',2:'external'}
        if sorted(v['facing'] for v in inventory) != sorted(names.get(v.get('facing'),'unknown') for v in base): issue('REALITY_CAMERA','cameras')
    carrier=values('carrier')
    if carrier.get('slots')==0 and carrier.get('sim_state')=='ready': issue('REALITY_LINK','carrier')
    web=values('webview'); version=web.get('version'); ua=web.get('user_agent',at(profile,'ua.value') or '')
    if version and f'Chrome/{version} ' not in ua: issue('REALITY_WEBVIEW','webview')
    packages=values('packages').get('installed',[])
    if len({p['package'] for p in packages})!=len(packages): issue('REALITY_DUPLICATE','packages')
    if any(p['package']==web.get('provider') and version and p['version_name']!=version for p in packages): issue('REALITY_WEBVIEW','webview')
    if any(p['package']=='android' and p['version_name']!=str(at(profile,'device.version_release')) for p in packages): issue('REALITY_LINK','packages')
    return issues


MESSAGES = {
 'REALITY_SCHEMA':'requires profile schema 0.4.', 'REALITY_TYPE':'has an invalid JSON type.',
 'REALITY_FIELD':'contains an unsupported field; private material is not accepted.',
 'REALITY_REQUIRED':'is required.', 'REALITY_ENUM':'is not an allowed value.',
 'REALITY_RANGE':'is outside the supported range.', 'REALITY_FORMAT':'has an invalid format.',
 'REALITY_DUPLICATE':'contains duplicate entries.', 'REALITY_AVAILABILITY':'known requires values; unknown/unavailable requires empty values.',
 'REALITY_LINK':'disagrees with the corresponding base profile field.',
 'REALITY_SOC':'SoC, GPU family, Android API or hardware label disagree.',
 'REALITY_STORAGE':'memory/storage shape is inconsistent.', 'REALITY_POWER':'power state is inconsistent.',
 'REALITY_MODE':'active dimensions/refresh must occur in supported modes.',
 'REALITY_SENSOR':'sampling delays or resolution/range are inconsistent.',
 'REALITY_CAMERA':'camera count or camera IDs are inconsistent.',
 'REALITY_WEBVIEW':'WebView version, package version and user agent disagree.',
}
