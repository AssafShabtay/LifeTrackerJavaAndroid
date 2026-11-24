from pathlib import Path
from docx import Document
from docx.shared import Pt
doc=Document()
style=doc.styles['Normal']
style.font.name='Consolas'
style.font.size=Pt(9)
style.paragraph_format.space_after=Pt(0)
style.paragraph_format.line_spacing=1
doc.add_heading('נספח קוד המקור',0)
sources=sorted(Path('app/src/main/java').rglob('*.java'))
for source in sources:
    doc.add_page_break()
    doc.add_heading(source.stem,2)
    doc.add_paragraph(source.as_posix())
    doc.add_paragraph(source.read_text(encoding='utf-8-sig').expandtabs(4).rstrip())
doc.save('tmp/documents/LifeTracker_Source_Appendix.docx')
print(len(sources))
