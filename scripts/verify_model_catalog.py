#!/usr/bin/env python3
"""Check pinned public model metadata and a 64-byte ranged response; never download multi-GB weights."""
import re, pathlib, urllib.request, json

source=pathlib.Path("core/src/main/kotlin/org/studyfuse/core/ModelDownload.kt").read_text()
models=re.findall(r'ModelArtifact\("([^"]+)","([^"]+)","(https://huggingface.co/[^"]+)",\s*"([0-9a-f]{64})",(\d+)L,(\d+)\)',source)
assert len(models)==2,"Expected both pinned vision artifacts"
results=[]
for ident,name,url,digest,size,ram in models:
    pointer_url=url.replace("/resolve/","/raw/")
    with urllib.request.urlopen(urllib.request.Request(pointer_url,headers={"User-Agent":"StudyFuse-model-verification/1.3"}),timeout=45) as response:
        pointer=response.read(8192).decode()
    assert re.search(r"oid sha256:"+digest+r"\b",pointer),(ident,"Publisher SHA mismatch")
    assert re.search(r"size "+size+r"\b",pointer),(ident,"Publisher size mismatch")
    with urllib.request.urlopen(urllib.request.Request(url,headers={"Range":"bytes=0-63","Accept-Encoding":"identity"}),timeout=45) as response:
        sample=response.read(64)
        assert len(sample)==64
        assert "html" not in response.headers.get("Content-Type","").lower()
        assert response.url.startswith("https://")
        if response.status==206:assert response.headers.get("Content-Range")==f"bytes 0-63/{size}"
    results.append({"model":ident,"sha256":digest,"bytes":int(size),"public_metadata_verified":True,"https_sample_bytes":64})
print(json.dumps({"verified":True,"models":results,"note":"This is not a full model download, native compatibility test or handwriting benchmark."},indent=2))
