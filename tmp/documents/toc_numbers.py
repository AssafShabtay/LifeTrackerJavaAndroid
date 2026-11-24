from pathlib import Path
from docx import Document
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
f=Path('output/doc/LifeTracker_Project_Portfolio.docx')
d=Document(f);pages=[3,4,5,11,14,16,46,47,48,51,52,53,54]
for q in d.paragraphs:
 links=q._p.xpath('w:hyperlink')
 if not links:continue
 i=int(links[0].get(qn('w:anchor')).removeprefix('Chapter'))
 field=OxmlElement('w:fldSimple');field.set(qn('w:instr'),f' PAGEREF Chapter{i} \\h ')
 run=OxmlElement('w:r');txt=OxmlElement('w:t');txt.text=str(pages[i]);run.append(txt);field.append(run)
 q.add_run('     ·     ');q._p.append(field)
d.save(f)

