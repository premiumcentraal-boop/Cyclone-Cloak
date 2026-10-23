import json, subprocess, threading, time, re, shlex, hashlib
from pathlib import Path
from datetime import datetime, timezone
D=None
P=None
OUT=None
PROC=None
THREAD=None
START=0
COLLECTED=False

def save(name,value):
    (OUT/name).write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')

def root(command):
    return D.root(command,check=False,timeout=20).stdout

def snapshot(label):
    result={}
    for name in ('status','stat','maps'):
        result[name]=root(f'cat /proc/{P}/{name}')[:1048576]
    result['threads']=root(f'for t in /proc/{P}/task/*; do cat "$t/status"; done')[:1048576]
    save(label+'-process.json',result)
    return result

def start(d,pid,user,phase,out):
    global D,P,OUT,PROC,THREAD,START
    D=d;P=int(pid);OUT=Path(out);START=time.time()
    cmd=root(f'cat /proc/{P}/cmdline').replace('\x00','').strip()
    if cmd!='dev.cyclone.cloak.probe.diag': raise RuntimeError('OBSERVER_PID_PACKAGE_MISMATCH')
    save('phase-start.json',dict(phase=phase,pid=P,user=user,utc=datetime.now(timezone.utc).isoformat(),device_time=D.shell('date -Iseconds').stdout.strip()))
    state=snapshot('before-warm')
    paths=set(re.findall(r'(/[^\s]+)',state['maps']))
    libraries={}
    for path in sorted(paths):
        if any(s in path.lower() for s in ('cloak','pine','shadowhook')) and not path.startswith('/memfd:'):
            libraries[path]=root('sha256sum '+shlex.quote(path)).strip()
    save('module-state.json',dict(module_prop=root('cat /data/adb/modules/cyclone_cloak/module.prop'),libraries=libraries,
        installed_libraries=root('sha256sum /data/adb/modules/cyclone_cloak/zygisk/*.so'),maps_sha256=hashlib.sha256(state['maps'].encode()).hexdigest(),
        limitation='Anonymous and deleted mappings cannot be rehashed through their former path; complete map ranges are retained.'))
    PROC=subprocess.Popen([D.adb,'-s',D.serial,'shell','su','--mount-master','-c',shlex.quote(f'logcat -b all --pid={P} -v threadtime -T 1')],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    def read():
        total=0
        with (OUT/'probe-full-logcat.log').open('wb') as f:
            while True:
                line=PROC.stdout.readline()
                if not line:break
                if total+len(line)<=2097152:f.write(line);f.flush();total+=len(line)
    THREAD=threading.Thread(target=read,daemon=True);THREAD.start()

def crashed():
    global COLLECTED
    if COLLECTED or D is None:return
    save('crash-process.json',dict(pid=P,exit_info=D.shell('dumpsys activity exit-info dev.cyclone.cloak.probe.diag').stdout))
    for attempt in range(8):
        listing=root('ls -t /data/tombstones/tombstone_[0-9][0-9]').splitlines()
        for path in listing[:32]:
            if not re.fullmatch(r'/data/tombstones/tombstone_\d\d',path):continue
            header=root('head -n 30 '+path)
            if not re.search(r'pid: '+str(P)+r',',header) or '>>> dev.cyclone.cloak.probe.diag <<<' not in header:continue
            text=root('cat '+path)[:2097152]
            # Preserve native stacks, register state, metadata and all memory mappings.
            # Raw memory dumps/ASCII are private and excluded from shareable evidence.
            private=OUT/'private';private.mkdir(exist_ok=True);(private/'.gitignore').write_text('*\n')
            (private/'matching-tombstone.txt').write_text(text,encoding='utf-8')
            lines=[];memory=False
            for line in text.splitlines():
                if re.match(r'\s*memory near |\s*memory and tag dump',line):memory=True;lines.append('[raw memory dump omitted]');continue
                if memory and (not line.strip() or re.match(r'\S',line)):memory=False
                if not memory:lines.append(line)
            (OUT/'matching-tombstone.log').write_text('\n'.join(lines)+'\n',encoding='utf-8')
            save('tombstone-provenance.json',dict(device_path=path,pid=P,original_sha256=hashlib.sha256(text.encode()).hexdigest(),bytes=len(text.encode()),redaction='Raw memory dump lines excluded; original retained locally under ignored private directory.'))
            COLLECTED=True;return
        time.sleep(1)
    save('tombstone-missing.json',dict(pid=P,reason='NO_MATCHING_TOMBSTONE_AFTER_BOUNDED_WAIT'))

def finish():
    if D is None:return
    snapshot('after-warm')
    save('phase-end.json',dict(pid=P,utc=datetime.now(timezone.utc).isoformat(),elapsed_seconds=round(time.time()-START,3),matching_tombstone=COLLECTED))
    if PROC is not None:
        PROC.terminate()
        try:PROC.wait(timeout=5)
        except subprocess.TimeoutExpired:PROC.kill()
    if THREAD is not None:THREAD.join(timeout=3)
