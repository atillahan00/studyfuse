#!/usr/bin/env python3
"""Independent MuPDF verification of actual PDFs produced by the Kotlin engine tests.
Development only; requires PyMuPDF. No document is uploaded.
"""
import json
from pathlib import Path
import fitz

root=Path(__file__).resolve().parents[1]
artifacts=root/'app/build/test-artifacts'
checks=[]
with fitz.open(artifacts/'mixed.pdf') as doc:
    assert doc[0].get_pixmap().pixel(50,70)==(255,0,0), 'Red image changed'
    assert doc[1].get_pixmap().pixel(160,200)==(0,0,255), 'Blue PDF changed'
    checks.append('Mixed image/PDF colours verified with an independent renderer')
report=json.loads((artifacts/'tall-report.json').read_text())
markers=json.loads((root/'fixtures/tall-markers.json').read_text())
mappings=report['files'][0]['coverage']
margin=report['settings']['margin']
with fitz.open(artifacts/'tall-output.pdf') as doc:
    assert len(doc)==report['generatedPages'], 'Missing output pages'
    pixmaps={}
    for i,marker in enumerate(markers):
        x,y=marker['x'],marker['y']
        matching=[m for m in mappings if m['sourceBounds'][0]+2<x<m['sourceBounds'][2]-2
                  and m['sourceBounds'][1]+2<y<m['sourceBounds'][3]-2]
        assert matching, f'No page contains source marker {i}'
        mapping=matching[0]
        page=mapping['outputPage']-1
        pix=pixmaps.setdefault(page, doc[page].get_pixmap())
        left,top,_,_=mapping['sourceBounds']
        actual=pix.pixel(round(margin+(x-left)*mapping['scale']),round(margin+(y-top)*mapping['scale']))
        assert all(abs(a-b)<14 for a,b in zip(actual,marker['rgb'])), (i,actual,marker['rgb'])
    assert any('200 Biologie' in page.get_text() for page in doc), 'Final source text missing'
    checks.append(f'All {len(markers)} tall-page markers verified, including the final source band')
    checks.append(f'{len(doc)} output pages agree with the completeness report')
with fitz.open(artifacts/'jpx-output.pdf') as doc:
    assert doc[0].get_pixmap().pixel(100,100)==(255,0,0), 'JPX encoded image lost'
    checks.append('JPX image retained and independently visible; application flags REVIEW')
print(json.dumps({'passed':True,'renderer':f'PyMuPDF {fitz.VersionBind}','checks':checks},ensure_ascii=False,indent=2))
