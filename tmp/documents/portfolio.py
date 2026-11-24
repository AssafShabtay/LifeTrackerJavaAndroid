from pathlib import Path
import re,json,collections,zipfile
from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Cm,Pt,RGBColor
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.enum.style import WD_STYLE_TYPE
from docx.enum.table import WD_TABLE_ALIGNMENT
base=Path('tmp/documents')
d=Document();s=d.sections[0];s.page_width=Cm(21);s.page_height=Cm(29.7);s.top_margin=Cm(1.95);s.bottom_margin=Cm(1.8);s.left_margin=s.right_margin=Cm(2.05)
def el(n,**attrs):
 e=OxmlElement('w:'+n)
 for k,v in attrs.items(): e.set(qn('w:'+k),str(v))
 return e
def font(st,name,size):
 st.font.name=name;st.font.size=Pt(size);rp=st.element.get_or_add_rPr();rp.rFonts.set(qn('w:cs'),name);rp.append(el('szCs',val=int(size*2)))
for n,z in [('Normal',11),('Title',30),('Heading 1',21),('Heading 2',16),('Heading 3',13)]:
 st=d.styles[n];font(st,'Arial',z);st.font.color.rgb=RGBColor.from_string('172C3C' if 'Heading' in n else '111111')
 st.paragraph_format.space_after=Pt(5);st.paragraph_format.line_spacing=1.12
cs=d.styles.add_style('Source listing',WD_STYLE_TYPE.PARAGRAPH);font(cs,'Consolas',8);cs.paragraph_format.space_after=Pt(0);cs.paragraph_format.line_spacing=1;cs.paragraph_format.widow_control=False
def rtl(p):
 p._p.get_or_add_pPr().append(el('bidi'));p.alignment=WD_ALIGN_PARAGRAPH.RIGHT;return p
def p(t='',style=None):
 q=rtl(d.add_paragraph(style=style));q.add_run(t);return q
def field(q,instr,value):
 r=q.add_run();r._r.append(el('fldChar',fldCharType='begin'));r=q.add_run();t=el('instrText');t.set('{http://www.w3.org/XML/1998/namespace}space','preserve');t.text=' '+instr+' ';r._r.append(t);q.add_run()._r.append(el('fldChar',fldCharType='separate'));q.add_run(value);q.add_run()._r.append(el('fldChar',fldCharType='end'))
def h(t,l=2):
 q=p(t,'Heading '+str(l))
 if l==1:q.paragraph_format.page_break_before=True
 return q
chapters=['טבלת תכולות הפרויקט','מבוא','מדריך למשתמש','תיאור תחום הידע','מבנה וארכיטקטורה','מימוש הפרויקט והמחלקות','ירושה','שיקולי תכן','שמירת נתונים','תרשימי זרימה','רפלקציה אישית','ביבליוגרפיה','נספח קוד המקור']
def chapter(i):
 q=h(chapters[i],1);q._p.insert(1,el('bookmarkStart',id=i+1,name='Chapter'+str(i)));q._p.append(el('bookmarkEnd',id=i+1))
def mark(t): 
 q=p('[כאן להוסיף '+t+']');q.runs[0].italic=True;q.runs[0].font.color.rgb=RGBColor.from_string('435F70')
def table(rows):
 t=d.add_table(rows=0,cols=len(rows[0]));t.style='Light Shading Accent 1';t.alignment=WD_TABLE_ALIGNMENT.CENTER;t._tbl.tblPr.append(el('bidiVisual'))
 for raw in rows:
  row=t.add_row();row._tr.get_or_add_trPr().append(el('cantSplit'))
  for c,x in zip(row.cells,raw):rtl(c.paragraphs[0]).add_run(str(x))
 t.rows[0]._tr.get_or_add_trPr().append(el('tblHeader'))
def code(txt):
 for chunk in re.findall(r'(?:[^\n]*\n?){1,16}',txt.expandtabs(4).strip('\n')):
  if chunk.strip():d.add_paragraph(chunk.rstrip('\n'),style='Source listing')
head=rtl(s.header.paragraphs[0]);head.add_run('LifeTracker   |   תיק פרויקט');foot=s.footer.paragraphs[0];foot.alignment=WD_ALIGN_PARAGRAPH.CENTER;field(foot,'PAGE','1')
q=p('LifeTracker','Title');q.paragraph_format.space_before=Pt(95);p('תיק פרויקט בהנדסת תוכנה').runs[0].font.size=Pt(19);p('חלופת תכנון ותכנות מערכות טלפונים ניידים  •  883589')
for lab in ['שם התלמיד','תעודת זהות','בית הספר','שם המנחה','תאריך ההגשה']:p(lab+'   [תמלא כאן]')
mark('לוגו של בית הספר')
q=p('תוכן עניינים','Title');q.paragraph_format.page_break_before=True
def fldpart(q,kind):q.add_run()._r.append(el('fldChar',fldCharType=kind))
q=p();fldpart(q,'begin');r=q.add_run();ins=el('instrText');ins.text=' TOC \\o "1-2" \\h \\z \\u ';r._r.append(ins);fldpart(q,'separate')
for i,title in enumerate(chapters):
 q=p();a=el('hyperlink',anchor='Chapter'+str(i));r=el('r');t=el('t');t.text=title;r.append(t);a.append(r);q._p.append(a)
fldpart(q,'end');d.settings.element.append(el('updateFields',val='true'))
chapter(0)
table([['תחום','מה מומש','היכן בקוד'],['שירות והתראות','תיעוד מיקום ותנועה בעת שימוש באפליקציה אחרת והצגת התראה בזמן המעקב','LocationService'],['אירועי מערכת','קליטת מעבר בין פעילויות, כניסה ויציאה מאזור ואתחול מכשיר','ActivityTransitionReceiver, GeofenceBroadcastReceiver, BootReceiver'],['מסד נתונים','שמירה מקומית של שהיות, תנועות, מקומות ונקודות מסלול','ActivityDatabase, ActivityDao, PlaceDao'],['ממשק משתמש','שלוש לשוניות, מפה, לוח שנה, רשימות וטופסי עריכה','MainActivity, HomeFragment, TimelineAdapter'],['נתוני שימוש','חישוב זמן מסך, פתיחות נעילה ושימוש לפי יום או שבוע','UsageStatsHelper, ScreenTimeViewModel'],['שירות חיצוני','הצגת מפות והצעות כתובת, אימות חשבון, גיבוי ושאלות על היומן','MapManager, AddressAutocompleteHelper, LoginActivity, BackupManager, AskTimelineLlmClient'],['עבודה מתוזמנת','הפעלת גיבוי מחזורי בתנאי רשת וסוללה מתאימים','AutoBackupWorker, SettingsFragment'],['העדפות','מראה האפליקציה, תחילת השבוע ובחירת גיבוי אוטומטי','SharedPreferences']])
chapter(1)
h('הרקע לפרויקט');p('בסוף יום לימודים קל לזכור שהייתי בבית הספר, אבל קשה יותר להיזכר מתי יצאתי, כמה זמן נמשכה הדרך הביתה ובאיזה חלק של היום השתמשתי בטלפון. בחרתי לפתח את LifeTracker כדי שיהיה אפשר לחזור ליום שכבר עבר ולענות על שאלות כאלה בלי לנהל יומן ידני.')
p('האפליקציה מיועדת למשתמשי Android שרוצים להבין איך הזמן שלהם מתחלק. תלמיד יכול לבדוק כמה זמן נמשכה הנסיעה ללימודים, וסטודנט או עובד יכול לבחור ביקור מסוים ולראות כמה השתמש בטלפון במהלכו.')
h('מטרת המערכת');p('המטרה היא לחבר בין שלושה פרטי מידע שבדרך כלל בודקים בנפרד: איפה הייתי, איך עברתי ממקום למקום ומה היה היקף השימוש בטלפון. ציר הזמן מציב את השהיות והתנועות לפי שעות. כשבוחרים פריט, אפשר להסתכל על המיקום שלו במפה ועל נתוני השימוש שמתאימים לאותו פרק זמן.')
h('יכולות המערכת');p('האפליקציה מתעדת שהיות ותנועות לפי המידע שזמין במכשיר. המשתמש יכול לבחור יום בלוח השנה, לצפות במסלול שנמדד, ללחוץ על ביקור כדי לערוך את פרטיו או להוסיף פעילות חסרה. במסך הסטטיסטיקה הוא יכול להשוות בין שעות ובין ימים ולבדוק באילו אפליקציות השתמש. הוא יכול גם לשאול שאלה על היומן, ולגבות את מסד הנתונים לאחר כניסה לחשבון.')
h('תהליך המחקר');p('במבוא לפרויקט השוויתי בין Google Maps Timeline לבין Digital Wellbeing. הראשון מתעניין במסלול ובמקומות שביקרתי בהם. השני מתמקד בשימוש בטלפון. השאלה שרציתי לענות עליה חיברה בין השניים: כמה מהזמן שהייתי במקום מסוים עבר בשימוש בטלפון? כדי לענות עליה צריך לדעת מתי התחיל הביקור ומתי הסתיים, ואז לחשב את השימוש באותו טווח.')
p('ההבחנה הזאת השפיעה על צורת השמירה. ביקור מקבל זמני התחלה וסיום, ותנועה יכולה לכלול רצף מדידות מיקום. כשצריך להציג שימוש בטלפון, האפליקציה פונה לנתוני השימוש של Android. כך אפשר לחשב את זמן המסך של ביקור גם אם הוא ארך רק חלק משעה.')
h('אתגרים מרכזיים');p('טלפון שמונח על שולחן יכול לדווח על שתי נקודות מיקום שונות במקצת. אם כל מדידה הייתה פותחת ביקור, שיעור אחד היה עלול להופיע בציר הזמן ככמה שהיות קצרות. המימוש בודק אם כבר קיימת שהייה פעילה סמוך למדידה החדשה. כאשר היא קרובה מספיק, הוא ממשיך את אותה הרשומה. אם המיקום רחוק, הוא סוגר את השהייה הקודמת ובודק את תחילת הבאה.')
p('גם תדירות המדידה חשובה. במהלך תנועה רצוי לקבל מספיק נקודות כדי לצייר את המסלול. כשהמשתמש נשאר באותו מקום וכבר נמצא עבורו מיקום, אפשר להפסיק את עדכוני השהייה התכופים. פתרון נוסף נוגע לזמן המסך: אם לשתי אפליקציות יש טווחי שימוש חופפים, צריך לאחד את הטווחים לפני שמחשבים את הזמן הכולל.')
chapter(2)
h('כניסה והרשמה');p('האפליקציה נפתחת במסך הראשי. אפשר להשתמש ביומן המקומי גם ללא חשבון. כדי לשמור עותק בענן נכנסים להגדרות ובוחרים בכניסה לחשבון. במסך שנפתח אפשר להיכנס עם כתובת דוא״ל וסיסמה, לפתוח חשבון, לבקש הודעת איפוס סיסמה או לחזור לשימוש ללא חשבון.')
p('מקלידים כתובת דוא״ל תקינה. בכניסה או בהרשמה דרושה סיסמה באורך שישה תווים לפחות. בזמן שהבקשה נשלחת, הכפתורים מושבתים זמנית. לאיפוס סיסמה מספיקה כתובת הדוא״ל. לאחר בקשה מוצלחת נשלחת הודעה לכתובת זו.')
mark('צילום של מסך הכניסה ובו אפשרויות ההרשמה, איפוס הסיסמה והמשך ללא חשבון')
h('תרשים מסכים');mark('תרשים המציג את המעברים מהמסך הראשי לבית, לסטטיסטיקה ולהגדרות, ואת החלונות שנפתחים מכל לשונית')
old=Document(next(Path('output/doc').glob('LifeTracker_5*'))).paragraphs
def reuse(a,b,skip=()):
 for i in range(a,b+1):
  if i in skip:continue
  t=old[i].text.strip()
  if not t:continue
  sty=old[i].style.name
  if sty.startswith('Heading'):h(t,int(sty[-1]))
  else:p(t.replace(';','.'))
h('המסך הראשי והניווט');p('בשורת הניווט למטה מופיעים Home, Statistics ו־Settings. מסך הבית מרכז את היום הנבחר. Statistics פותח את נתוני זמן המסך ואת השיחה על היומן. Settings מרכז הרשאות, העדפות והגדרות חשבון וגיבוי. אפשר לחזור לאחת הלשוניות בלי להפעיל מחדש את האפליקציה.')
reuse(803,804);mark('צילום של מסך הבית עם ביקור ותנועה על המפה')
reuse(805,807);mark('צילום של לוח השנה ובו יום שיש עבורו נתונים')
reuse(808,821);p('אם הכתובת עדיין בבדיקה, מחכים לסיום הבדיקה לפני ששומרים. לשהייה בלי שם האפליקציה נותנת את השם Custom Activity.');mark('צילום של טופס הוספת שהייה ושל בחירת הסמל והצבע')
reuse(824,829);mark('צילום של חלון הוספת תנועה')
reuse(830,837);mark('צילום של חלונות העריכה לביקור ולתנועה')
reuse(838,841);mark('צילום של בחירת נקודה וכתובת במפה')
reuse(842,850,skip=[843]);mark('צילום של מסך הסטטיסטיקה בתצוגה יומית ובתצוגה שבועית')
h('שאלות על ציר הזמן');p('מתוך לשונית הסטטיסטיקה פותחים את חלון השאלות, מקלידים שאלה ולוחצים על שליחה. לדוגמה, אפשר לשאול באילו ימים בשבוע האחרון הופיעה הליכה. התשובה מתבססת על השהיות והתנועות שנרשמו ב־30 הימים האחרונים. תאריך שמופיע כקישור פותח את אותו יום במסך הבית. אם השירות אינו זמין, מופיעה הודעה המציעה לבדוק את החיבור ולנסות שוב.')
mark('צילום של חלון השיחה עם שאלה ותשובה שמפנה ליום בציר הזמן')
reuse(864,868);h('גיבוי ושחזור');p('לאחר כניסה לחשבון אפשר ליצור גיבוי של היומן בענן. אם קיים גיבוי קודם, האפליקציה מציגה את התאריך שלו ומבקשת אישור לדריסה. בשחזור מוצגים תאריך הגיבוי וגודלו. אישור השחזור מחליף את היסטוריית המיקום והמקומות השמורים במכשיר, ולכן חשוב לבדוק איזה עותק בוחרים.')
p('אפשר להפעיל גיבוי אוטומטי. האפליקציה מתזמנת אותו במחזור של 24 שעות ומחכה לחיבור שאינו מוגבל לפי נפח ולמצב שבו הסוללה אינה נמוכה. את עותק הענן אפשר למחוק מתוך ההגדרות. מחיקת היסטוריה מקומית ומחיקת עותק הענן הן בחירות נפרדות כשיש חשבון מחובר.')
mark('צילום של ההגדרות ובו מצב החשבון ואפשרויות הגיבוי')
reuse(874,883,skip=[877]);h('דרישות התקנה, הרשאות ומגבלות');p('גרסת המינימום היא Android 7.0 (API 24). הפרויקט נבנה מול SDK 36. לבנייה בסביבת הפיתוח פותחים את תיקיית הפרויקט ב־Android Studio, משלימים סנכרון Gradle, בוחרים מכשיר יעד ומפעילים את מודול app. המפות והחיפוש משתמשים בשירותי Google, והגיבוי, ההתחברות והשיחה זקוקים לחיבור לשירותי הענן המוגדרים לפרויקט.')
h('התקנה מקובץ APK');reuse(785,787);reuse(790,800);mark('צילום של בקשת הרשאה במסך הפתיחה');h('מכשירים וגרסאות שעליהם נבדקה האפליקציה');p('[תמלא כאן]')
h('תוצאות הבדיקות');p('[תמלא כאן]')
chapter(3);reuse(1,44)
chapter(4)
h('תכנון המסכים');p('MainActivity היא נקודת הכניסה המוגדרת במניפסט. היא מחזיקה שלושה Fragments. מעבר בתפריט התחתון מציג אחד מהם ומסתיר את הקודם. המפה לבחירת כתובת ומסך ההתחברות נפתחים כ־Activities נפרדות. הוספה, עריכה ושיחה נפתחות כחלונות מעל המסך שממנו המשתמש הגיע.')
table([['מסך או חלון','מי פותח אותו','מה מחזיר או מעדכן'],['בית ולוח שנה','פתיחת היישום או Home','בחירת יום משנה את היומן, המפה וכרטיס השימוש.'],['הוספה ועריכה','כפתור ההוספה או עריכת שורה בבית','שמירת הטופס מעדכנת את המסד ואת רשימת היום.'],['בחירת נקודה במפה','שדה הכתובת בטופס','הנקודה שנבחרה חוזרת לטופס.'],['סטטיסטיקה','Statistics או כרטיס השימוש בבית','בחירת יום או שבוע מעדכנת את הגרפים.'],['שיחה על היומן','לשונית הסטטיסטיקה','לחיצה על תאריך בתשובה פותחת את היום בבית.'],['חשבון','הגדרות','התחברות מאפשרת פעולות על גיבוי הענן.']])
h('שכבות וקשרים בין המחלקות');p('שכבת התצוגה נמצאת בעיקר בחבילת ui. היא מטפלת בלחיצות, במפות, בגרפים ובטפסים. בחבילת tracking נמצאת הלוגיקה שמפרשת אירועי פעילות ומיקום וקוראת נתוני שימוש. בחבילת data נמצאים אובייקטי המידע, הגישה למסד והגיבוי. למשל, מסך הבית יכול לקרוא ביקור שמור גם כשהשירות שיצר אותו אינו מטפל באותו רגע בעדכון מיקום.')
p('למסך זמן המסך יש ViewModel משלו. הוא שומר את בחירת התקופה ומפרסם תוצאות דרך LiveData. במסך הבית חלוקת האחריות שונה: ה־Fragment מתאם ישירות בין מנהל לוח השנה, המפה, מתאם הרשימה וכרטיס השימוש. מסד Room הוא נקודת המפגש בין המעקב ברקע לבין התצוגה.')
h('שילוב השירותים');table([['רכיב','תפקידו ביישום'],['Google Play services','מדידות מיקום, הודעות על שינוי סוג הפעילות ואירועים מגבולות גאוגרפיים.'],['Google Maps ו־Places','ציור סמנים ומסלולים במפה והשלמת הצעות כתובת.'],['Android UsageStatsManager','קריאת אירועי שימוש במכשיר לאחר שהמשתמש אישר גישה מיוחדת.'],['Firebase Authentication ו־Storage','זיהוי החשבון ושמירת קובץ גיבוי בנתיב של המשתמש.'],['Firebase AI','שליחת שאלה ותיאור של רשומות היומן למודל Gemini דרך Vertex AI.'],['WorkManager','הרצת משימת הגיבוי המחזורית בהתאם לתנאים שקבעה האפליקציה.']]);mark('תרשים UML של הקשרים בין MainActivity, המסכים, LocationService, רכיבי המעקב, שני ממשקי DAO ומחלקות המודל')

chapter(5)
sources={f.stem:f for f in Path('app/src/main/java').rglob('*.java')}
roles=dict(line.split('|',1) for line in (base/'roles.txt').read_text(encoding='utf-8-sig').splitlines())
assert set(sources)==set(roles),set(sources)^set(roles)
members=collections.defaultdict(list)
for line in (base/'members.tsv').read_text(encoding='utf8').splitlines():
 name,kind,ident,typ,mods=line.split('\t');members[name].append((kind,ident,typ,mods))
def src(name):return sources[name].read_text(encoding='utf-8-sig')
def sample(name,start,end=None,count=8):
 lines=src(name).splitlines();a=next(i for i,x in enumerate(lines) if start in x);b=next((i+1 for i in range(a,len(lines)) if end and end in lines[i]),a+count)
 import textwrap
 code(textwrap.dedent('\n'.join(lines[a:b])))
examples={
'ActivityMergeManager':('המשך שהייה שכבר נפתחה','בדוגמה הזאת לשהייה ולמדידה החדשה יש קואורדינטות. המרחק נבדק מול סף של 75 מטר. כאשר הוא קטן מהסף, נשמרת אותה רשומה וזמן הסיום שלה נשאר פתוח. לכן התצוגה עדיין מראה ביקור אחד.','float distance = distanceInMeters','dao.updateStillEndTime'),
'ActivityDao':('קריאת ביקורים שחופפים לטווח המבוקש','השאילתה מחזירה גם שהייה שהתחילה אתמול ונמשכה אחרי חצות. די בכך שההתחלה קודמת לסוף הטווח ושזמן הסיום חל בתוך הטווח או אחריו. אם אין זמן סיום, השהייה נחשבת פתוחה.','@Query("SELECT * FROM still_locations WHERE startTimeDate <= :end AND (endTimeDate IS NULL OR endTimeDate >= :start)")','getStillsFromRange(Date'),
'UsageStatsHelper':('חיבור זמני שימוש חופפים','אחרי מיון הטווחים לפי זמן ההתחלה, המחלקה מרחיבה את הטווח הנוכחי כל עוד יש חפיפה. למשל, שימוש מ־16:00 עד 16:10 ושימוש מ־16:08 עד 16:15 תורמים יחד 15 דקות. ברגע שמתחיל טווח נפרד, משך הטווח הקודם מצטרף לסכום.','for (TimeRange range : valid)','currentStart = range.start;@@'),
'AutoBackupWorker':('מה קורה אם ההעלאה נכשלה','העבודה בודקת תחילה שיש משתמש מחובר. במקרה של כשל בהעלאה היא מחזירה Result.retry(), כך שמנגנון התזמון יכול להריץ ניסיון נוסף.','if (FirebaseAuth.getInstance().getCurrentUser() == null)',': Result.retry();'),
'AskTimelineLlmClient':('בחירת הרשומות שנשלחות עם השאלה','תחילת הטווח מחושבת לפי התאריך הנוכחי פחות 30 ימים. שתי השאילתות מחזירות ביקורים ותנועות, ומהם נבנה תיאור טקסטואלי לפי סדר הזמנים. המשתמש יכול לפתוח יום שהופיע בתשובה ולבדוק את הרשומות בעצמו.','cal.add(Calendar.DAY_OF_YEAR, -30);','dao.getMovementsFromRange(startDate, endDate);'),
'AddCustomMovementActivitySheet':('שמירת תנועה שהוזנה ידנית','הבחירה בטופס קובעת איזה אובייקט תנועה ייווצר. לאחר הצבת זמני ההתחלה והסיום, המחלקה מוסיפה את התנועה למסד. החזרה לממשק נעשית בנפרד, לאחר פעולת הכתיבה.','MovementActivity newMovement =','activityDao().insertMovementActivity(newMovement);')}
groups=[
('מודל הפעילות והמקומות','BaseActivity TimelineItem StillLocation Place MovementActivity WalkingActivity RunningActivity CyclingActivity DrivingActivity UnknownMovementActivity RoutePoint'),
('מסד הנתונים והגיבוי','ActivityDatabase ActivityDao PlaceDao Converters BackupManager AutoBackupWorker'),
('הפעלת האפליקציה והמעקב','LifeTrackerApp MainActivity LocationService LocationProvider ActivityTrackingUtils ActivityMergeManager ActivityTransitionManager ActivityTransitionReceiver BootReceiver GeofenceManager GeofenceUtilsManager GeofenceBroadcastReceiver'),
('מדידת השימוש בטלפון','UsageStatsHelper AppCategoryHelper DailyUsageItem WeeklyUsageItem TimelineItemUsageData'),
('מסך הבית ומרכיביו','HomeFragment CalendarManager DayDataIndicatorView MapManager MapPickerActivity TimelineAdapter TimelineUsageBadgeHelper PhoneUsageMiniCardManager PhoneUsageDonutView'),
('טופסי הוספה ועריכה','BaseStillActivitySheet AddCustomStillActivitySheet EditStillActivitySheet BaseMovementActivitySheet AddCustomMovementActivitySheet EditMovementActivitySheet IconPickerDialog'),
('תצוגות הסטטיסטיקה','ScreenTimeFragment ScreenTimeViewModel ScreenTimeStatsManager DetailedPhoneUsageCardManager BaseUsageGraphView DailyUsageGraphView WeeklyUsageGraphView'),
('שאלות על ציר הזמן','AskTimelineBottomSheetFragment AskTimelineLlmClient TimelineChatSession ChatMessage ChatMessageAdapter DateLink'),
('חשבון, הגדרות וכלי עזר','LoginActivity SettingsFragment PermissionManager AddressAutocompleteHelper PlaceDropdownAdapter ContainsArrayAdapter ColorAndIcons UiFormatters Logger ErrorLogger')]
actions=[
('מחזור החיים והחיבור לממשק',r'^on(Create|ViewCreated|Destroy|Pause|Resume|Start|SaveInstanceState|Bind|Cleared|SizeChanged|Measure|TouchEvent)'),
('טעינת נתונים וקריאה',r'^(get|load|fetch|find|read|query|detect)'),('שמירה ועדכון',r'^(save|update|insert|replace|delete|clear|copy|restore|backup|persist|set)'),('בדיקות וחישוב',r'^(is|has|can|check|calculate|validate|should|attempt|format|distance|build|convert)'),('תצוגה ופעולות משתמש',r'^(show|draw|render|bind|handle|setup|init|on|open|select|navigate|focus|refresh|filter)'),('ניהול משימות ואירועים',r'.*')]
def rolefield(typ,ident):
 if ident=='TAG':return 'תג המשמש לזיהוי המחלקה ביומן האירועים'
 if 'PREF' in ident or ident.startswith('KEY_'):return 'שם קובץ העדפות או מפתח לקריאה ושמירה של הגדרה'
 if 'Dao' in typ or 'Database' in typ:return 'גישה לקריאה או לכתיבה במסד הנתונים'
 if any(x in typ for x in ['Button','View','Layout','ProgressBar','Recycler','EditText','Drawable','Spinner','Chip','Paint','RectF','Dialog','Adapter','Image','Binding']):return 'רכיב תצוגה, ציור או חיבור של נתונים לממשק'
 if 'Listener' in typ or 'Callback' in typ:return 'קבלת הודעה על בחירה או על סיום פעולה'
 if any(x in typ for x in ['Executor','Handler','Future','Thread','Atomic','Runnable']):return 'הרצת משימות ותיאום בין עבודת הרקע לממשק'
 if typ in ['Context','Application','Activity','LifeTrackerApp','MainActivity','LocationService']:return 'קישור לרכיב Android שממנו מתבצעת הפעולה'
 if typ=='Date' or 'time' in ident.lower() or 'date' in ident.lower():return 'תאריך, משך זמן או מצב הקשור לטווח הנבחר'
 if 'LiveData' in typ:return 'ערך שניתן לעקוב אחר השינויים בו מתוך המסך'
 if ident.isupper():return 'ערך משותף המשמש בתנאי הפעולה או בהגדרות המחלקה'
 if typ=='boolean' or typ=='Boolean':return 'מצב שהמחלקה בודקת לפני המשך הפעולה'
 if 'List' in typ or 'Map<' in typ or '[]' in typ:return 'אוסף ערכים שהמחלקה מעבדת או מציגה'
 if typ.endswith(('Manager','Helper','Provider','Fragment','Client')):return 'רכיב שעמו המחלקה עובדת'
 return ''
p('המחלקות מסודרות לפי התפקיד שלהן. בכל קבוצה מופיעים השדות שהמחלקה מגדירה והפעולות שבאחריותה. public מציין גישה מחוץ למחלקה, private מציין גישה פנימית ו־protected מאפשר גישה גם למחלקות יורשות. סימון static פירושו שהשדה שייך למחלקה עצמה. כאשר הנראות אינה מצוינת בקוד, הגישה היא מתוך החבילה.')
notes={
'ActivityDao':[('replaceMovementWithStill','מוחקת את רשומת התנועה ומכניסה שהייה במסגרת עסקה אחת. בכך נמנע מצב שבו הפעולה הראשונה נשמרה והשנייה נכשלה.'),('updateStillAndSyncPlace','מעדכנת את הביקור ואת פרטי המקום הקשורים אליו. כך עריכה של מקום יכולה להשתקף בביקורים נוספים שמפנים אליו.')],
'PlaceDao':[('getPlaceIdFromGeofenceId','מחפשת לפי מזהה הגבול שנמסר למערכת המיקום ומחזירה את רשומת המקום המתאימה. כך אפשר לתרגם אירוע גאוגרפי למקום שמוכר למשתמש.')],
'ActivityTransitionManager':[('registerActivityTransitions','כאשר כבר מתנהל רישום, המאזין של הבקשה הנוספת מצטרף לרשימת ההמתנה. אם הרישום כבר הושלם, ההצלחה נמסרת מיד.'),('unregisterActivityTransitions','מסירה את ההרשמה לאירועי המעבר באמצעות אותו יעד ששימש בעת הרישום.')],
'LocationService':[('handleGeofenceUpdate','מפרשת אירוע הקשור לאזור גאוגרפי ומטפלת בקשר בין האזור לבין השהייה הפעילה.'),('startMovementTracking','מטפלת בתחילת קטע תנועה: בודקת את הפעילות הקיימת ומתחילה לאסוף את נתוני המסלול בהתאם למצב.'),('onDestroy','בעת סגירת השירות מתבצע ניקוי של משאבי המעקב והאזנה לעדכונים.')],
'LocationProvider':[('startRouteUpdates','מבקשת מדידות מיקום עבור מסלול התנועה. כל נקודה מקושרת לקטע התנועה כדי שאפשר יהיה לצייר בהמשך את הדרך הנכונה.'),('stopFrequentStillLocationUpdates','מפסיקה בקשות תכופות למיקום לאחר שהושג מיקום מתאים לשהייה.')],
'ActivityTrackingUtils':[('checkIfMovementIsStill','בודקת את הפיזור של מדידות המיקום כדי להחליט אם קטע שסווג כתנועה עשוי להתאים לשהייה.'),('distanceInMeters','מחשבת את המרחק בין שתי קואורדינטות במטרים. ערך זה משמש בהשוואה לסף האיחוד.')],
'UsageStatsHelper':[('getUsageDataForPeriod','מחזירה נתוני שימוש עבור טווח מבוקש. למשל, תג ליד ביקור בבית הספר מבקש את השימוש בין תחילת הביקור לסיומו.'),('getDoomscrollStats','מאתרת רצפים של שימוש באפליקציות שמסווגות כמתאימות למדד. סף הזמן הוא 15 דקות ביום ו־10 דקות בחלון הלילה. זהו אומדן לפי זמן שימוש, כיוון שהטלפון אינו מוסר כאן את תוכן המסך.')],
'HomeFragment':[('setSelectedDate','מקבלת תאריך גם ממסך אחר, למשל לאחר לחיצה על תאריך בתשובה בצ׳אט. הפעולה מעדכנת את היום שמוצג בבית.')],
'MapManager':[('showFullDay','מחזירה את תצוגת המפה מהתמקדות בפריט יחיד להצגת פריטי היום.'),('focusOnItem','מתמקדת בפריט שנבחר ברשימה. מזהה התנועה מאפשר לקרוא את נקודות המסלול של אותו פריט בלבד.')],
'CalendarManager':[('hasDataForDate','מחשבת את תחילת היום ואת סופו ובודקת אם קיימים ביקורים או תנועות שחופפים לטווח. התוצאה קובעת אם יופיע סימון בתא של אותו תאריך.')],
'BackupManager':[('getBackupInfo','קוראת את המידע על העותק השמור, כדי שהממשק יוכל להציג תאריך וגודל לפני שחזור או דריסה.'),('processRestore','בודקת את גרסת המסד שהורד. לאחר התאמה היא עוצרת את שירות המעקב וסוגרת את החיבור למסד לפני החלפת הקובץ.')],
'SettingsFragment':[('updateAutoBackupWorker','מוסיפה עבודה מחזורית ייחודית כאשר הגיבוי האוטומטי פעיל, ומבטלת את התזמון כאשר הוא כבוי.'),('handleRestoreBackup','מציגה למשתמש את פרטי הגיבוי ומבקשת אישור לפני החלפת הנתונים המקומיים.')],
'TimelineChatSession':[('getChatHistory','מחזירה עותק של רשימת ההודעות כדי שהצגת השיחה לא תשנה בטעות את הרשימה המקורית.'),('clearHistory','מעלה את מספר הגרסה של השיחה, מסירה את ההודעות הישנות ומחזירה את הודעת הפתיחה.')],
'ScreenTimeFragment':[('setupObservers','מחברת את המסך לערכי ה־LiveData: שינוי בהרשאה, בטעינה או בתקופה הנבחרת מגיע לרכיב התצוגה המתאים.'),('onDestroyView','משחררת את הקישור למנהל התצוגה כשמסך הסטטיסטיקה יורד מהמסך.')],
'MovementActivity':[('createFromTypeName','מתאימה בין השם הטקסטואלי של סוג התנועה לבין המחלקה שיוצרת את הפריט המתאים. כך גם רשומה שנקראה מ־Room מקבלת את הסמל והצבע של הסוג שלה.')],
'LoginActivity':[('handleForgotPassword','בודקת את כתובת הדוא״ל ומבקשת מ־Firebase לשלוח אליה הודעת איפוס. המשתמש מקבל חיווי על הצלחה או כישלון.'),('handleContinueOffline','סוגרת את מסך ההתחברות ומחזירה את המשתמש למסך שממנו הגיע.')],
'GeofenceManager':[('getGeofencePendingIntent','מכינה את היעד שאליו Android ישלח הודעה כשיתקבל אירוע עבור אזור שנרשם.')],
'AskTimelineLlmClient':[('processResponseWithDateLinks','מחפשת בתשובה תאריכים בפורמט שהוגדר בבקשה למודל ומארגנת אותם כקישורים שאפשר לפתוח מתוך השיחה.')]
}
def cls(n):
 h(n,3);p(roles[n]);items=members[n];fields=[x for x in items if x[0]=='F'];methods=[x[1] for x in items if x[0]=='M']; grouped=collections.defaultdict(list);q=p('תכונות המחלקה');q.runs[0].bold=True
 for _,ident,typ,mods in fields:
  vis=next((v for v in ['public','private','protected'] if v in mods),'package')
  label=(vis+' '+('static ' if 'static' in mods else '')+('final ' if 'final' in mods else '')+typ).strip()
  grouped[(label,rolefield(typ,ident))].append(ident)
 if fields:
  for (label,meaning),names in grouped.items():
   q=p();q.paragraph_format.space_after=Pt(2);q.add_run(', '.join(names)+'  ('+label+')'); 
   if meaning:q.add_run(' — '+meaning+'.')
 else:p('אין שדות שמוגדרים ישירות ב־'+n+'.')
 q=p('פעולות המחלקה');q.runs[0].bold=True
 fields_lower={i[1].lower() for i in fields}
 methods=list(dict.fromkeys(x for x in methods if not (x.startswith(('set','get')) and x[3:].lower() in fields_lower)))
 for lab,pat in actions:
  matches=[x for x in methods if re.search(pat,x)]
  if matches:
   p(lab+' — '+', '.join(x+'()' for x in matches)+'.');methods=[x for x in methods if x not in matches]
 if not any(x[0]=='M' and not(x[1].startswith(('set','get')) and x[1][3:].lower() in fields_lower) for x in items):p('הפעולות משמשות לבנייה ולגישה לערכים שמתוארים למעלה.')
 nested=[k.split('.',1)[1] for k in members if k.startswith(n+'.')]
 if nested:p('רכיבים פנימיים המוגדרים בקובץ — '+', '.join(nested)+'.')
 if n in notes:
  for method,description in notes[n]:
   q=p();q.add_run(method+'()').bold=True;q.add_run(' — '+description)
 if n in examples:
  title,expl,a,b=examples[n];q=p(title);q.runs[0].bold=True;p(expl);sample(n,a,None if '@@' in b else b,12)
for g,names in groups:
 h(g)
 for n in names.split():cls(n)
assert sorted(n for _,ns in groups for n in ns.split())==sorted(sources)
h('קובצי תצורה ומשאבים')
table([['קובץ או תיקייה','תפקיד בפרויקט'],['AndroidManifest.xml','מגדיר את נקודת הכניסה, ההרשאות, המקלטים ואת שירות החזית מסוג location.'],['app/build.gradle.kts','מגדיר את גרסת היישום, רמות ה־SDK, גרסת Java, התלויות ותוסף הפקת הסכמה של Room.'],['settings.gradle.kts וקובץ הבנייה הראשי','קובעים אילו מודולים ומאגרים משתתפים בבנייה ואילו תוספים זמינים.'],['gradle/libs.versions.toml','מרכז גרסאות וכינויים לתלויות ולתוספי הבנייה.'],['res/layout ו־res/menu','מגדירים את מבנה המסכים, טופסי העריכה, שורות הרשימה ותפריט הניווט.'],['res/drawable, res/color ו־res/values','מספקים סמלים, רקעים, צבעים והגדרות מראה. משאבי values-night מתאימים את המראה למצב כהה.'],['res/raw/categories.json','מכיל נתוני קטגוריות המשמשים את טופס השהייה.'],['app/schemas','שומר את תיאור טבלאות Room לפי גרסת הסכמה.']])
chapter(6);h('המשותף לשהייה ולתנועה');p('שהייה ותנועה שונות בפרטים שלהן, אך שתיהן מתרחשות בפרק זמן. לכן StillLocation ו־MovementActivity יורשות מ־BaseActivity. הקוד המשותף של מזהה הרשומה ושל זמני ההתחלה והסיום מופיע פעם אחת. הממשק TimelineItem מאפשר לתצוגה לקבל את שני סוגי הרשומות כחלק מאותו יום.')
h('סוגי תנועה');p('הליכה, ריצה, רכיבה ונסיעה ברכב הן מחלקות יורשות של MovementActivity. כל אחת מחזירה שם סוג, צבע וסמל משלה. פעולת יצירה מרכזית בוחרת את המחלקה המתאימה לשם שנשמר בטבלה. אם המשתמש משנה את הסוג בטופס, פרטי הזמן עדיין שייכים לאותה תנועה.');sample('WalkingActivity','public final class',None,5)
h('טפסים וגרפים');p('טופס ליצירת ביקור וטופס לעריכתו זקוקים לאותם בוררי שעות ואותה בחירת מקום. שניהם יורשים מ־BaseStillActivitySheet. מחלקת ההוספה יוצרת רשומה חדשה ומחלקת העריכה מעדכנת רשומה קיימת. באופן דומה, הגרפים היומי והשבועי יורשים מ־BaseUsageGraphView ומשתמשים יחד בקוד המטפל בנגיעה ובהצגת ערך של עמודה.');mark('תרשים ירושה המציג את BaseActivity, סוגי התנועה ושתי משפחות הטפסים')
chapter(7)
h('מקום אחד, ביקורים רבים');p('אם הגעתי לבית הספר ביום ראשון ושוב ביום שני, אלה שני ביקורים שונים באותו מקום. שמירת השעות בטבלת הביקורים מאפשרת להציג כל יום בנפרד. שמירת המקום ברשומה משלו מאפשרת לזהות את בית הספר בביקורים חוזרים ולהשתמש שוב בפרטי התצוגה שלו.')
h('פעילות מתמשכת וחציית חצות');p('בעת פתיחת פעילות שעת הסיום יכולה להישאר ריקה. למשל, ביקור שמתחיל ב־23:40 ומסתיים ב־00:20 חופף לשני ימים. מסיבה זו השאילתה מסננת לפי חפיפה לטווח התאריכים. סינון לפי יום ההתחלה בלבד היה משמיט את הביקור מהיום השני.')
h('הפרדת עבודה ברקע מהממשק');p('כתיבה למסד, הכנת סיכום שבועי והעלאת גיבוי עשויות להימשך זמן. המחלקות מעבירות עבודה מתאימה לתורי רקע. כשהתוצאה מוכנה חוזרים לממשק, למשל כדי לרענן את ציר הזמן אחרי הוספת תנועה. במסך הסטטיסטיקה ברירת התקופה נשמרת בנפרד מהציור של הגרף.')
h('בחירה בין שמירה מקומית לשירות חיצוני');p('היומן נשמר במכשיר. חשבון דרוש כדי לקשר עותק גיבוי למשתמש ולשחזר אותו. לשאלה בצ׳אט יש מסלול אחר: הבקשה כוללת טקסט שמתאר ביקורים ותנועות ואת שאלת המשתמש. לכן זמינות השיחה תלויה בחיבור לשירות, והמידע בתשובה תלוי באיכות הרישומים שנאספו.')
chapter(8);h('מבנה המסד המקומי');p('הקובץ activity_database נשמר באמצעות Room מעל SQLite. גרסת הסכמה בקוד היא 4. הטבלאות הבאות מציגות את שמות העמודות כפי שהם נשמרים בפועל. ערך NULL בשעת הסיום משמש לייצוג פעילות פתוחה. נקודת מסלול ומקום שמור מקבלים כל אחד מזהה משלהם.')
schema=json.loads(next(Path('app/schemas').rglob('4.json')).read_text())['database']
meanings=dict(id='מזהה הרשומה',lat='קו רוחב',lng='קו אורך',startTimeDate='תחילת הפעילות',endTimeDate='סיום הפעילות',wasSupposedToBeActivity='סוג הפעילות שזוהה קודם',placeId='קישור למקום שמור',placeName='שם המקום בביקור',category='קטגוריית המקום',icon='שם הסמל',color='ערך הצבע',isStop='האם זו עצירה',placeAddress='כתובת הביקור',geofencePlaceId='מזהה המשמש בגבול הגאוגרפי',activityTypeName='שם סוג התנועה',startLat='קו רוחב בתחילת התנועה',startLng='קו אורך בתחילת התנועה',endLat='קו רוחב בסיום התנועה',endLng='קו אורך בסיום התנועה',name='שם המקום',address='כתובת המקום',movementActivityId='מזהה תנועת האב',timestamp='זמן המדידה')
for ent in schema['entities']:
 h(ent['tableName']);table([['עמודה','טיפוס במסד','תפקיד']]+[[f['columnName'],f['affinity'],meanings.get(f['columnName'],f['fieldPath'])] for f in ent['fields']])
p('מחיקת תנועה גוררת מחיקה של נקודות המסלול המקושרות אליה. לעומת זאת, מחיקת מקום שמור מאפסת את הקישור אליו בביקורים הקשורים. הביקור יכול להמשיך להתקיים עם הזמן והפרטים שנשמרו בו.');mark('תרשים קשרים בין ארבע הטבלאות, ובו קשר אחד לרבים ממקום לשהיות ומתנועה לנקודות מסלול')
h('נתוני שימוש והעדפות');p('היסטוריית השימוש שבידי Android נקראת לפי הטווח שנבחר וממנה מחושבים הנתונים היומיים והשבועיים. גיבוי קובץ Room מכיל את טבלאות היומן. היסטוריית השימוש שמנהלת מערכת ההפעלה נשארת במכשיר.')
table([['אחסון','מידע ודוגמה'],['MyPrefs','מפתחות כגון theme_mode, week_start_day, auto_backup_enabled ו־last_backup_time שומרים בחירות והעדפות לפעם הבאה.'],['permission_prefs','שומר אם הרשאה התבקשה ואם המשתמש בחר לדלג על גישת נתוני השימוש. מצב האישור עצמו נבדק מול Android.'],['זיכרון התהליך','TimelineChatSession שומרת את ההודעות ואת מצב הבקשה של השיחה הפעילה.']])
h('עותק גיבוי בענן');p('לפני ההעתקה פונה BackupManager ל־SQLite בבקשת WAL checkpoint. חלק מהשינויים האחרונים יכולים להימצא עדיין ביומן הכתיבה של המסד. הפעולה מעבירה אותם לקובץ הראשי כדי שהעותק יכלול אותם. קובץ זמני מועלה ל־Firebase Storage בנתיב users/<uid>/backup.db. העלאה חדשה משתמשת באותו נתיב.')
p('בשחזור מורידים תחילה לקובץ זמני ופותחים אותו לבדיקה לקריאה בלבד. אם גרסת המסד שבגיבוי שונה מהגרסה הנתמכת, מוצגת הודעת אי־התאמה. רק אחרי הבדיקה נסגר החיבור למסד המקומי והקובץ משוחזר. בגיבוי המחזורי WorkManager אחראי לבדוק מתי התנאים מאפשרים לבצע את העבודה.')
chapter(9)
for title,body,fig in [
('קבלת אירוע תנועה','המקלט מקבל את סוג הפעילות ואת המעבר, ומודיע ל־LocationService. השירות בודק מה פעיל כרגע. אם ניתן להמשיך רשומה מתאימה, היא מתעדכנת. כאשר מתחיל קטע חדש הוא נפתח במסד, ובמהלך תנועה אפשר לקשר אליו נקודות מסלול.','אירוע מעבר, פעילות קיימת, החלטת איחוד או פתיחת רשומה, ושמירה'),
('בחירת תאריך','המשתמש בוחר יום בלוח השנה. מסך הבית קורא שהיות ותנועות שחופפות ליום, מסדר אותן לפי הזמן ומרענן את הרשימה והמפה. כרטיס השימוש מבקש את נתוני הטלפון לאותו תאריך.','בחירת יום והסתעפות לקריאת היומן ולקריאת זמן המסך'),
('הוספת ביקור','הטופס נטען עבור היום שנבחר. המשתמש בוחר שעות ופרטי מקום. לאחר השמירה נמסרת הודעה למסך הבית כדי שיטען את הפעילויות המעודכנות. ביטול הטופס מחזיר את המשתמש ליומן.','הזנת ביקור, בחירת כתובת, שמירה או ביטול ורענון הבית'),
('חישוב זמן מסך','המסך בודק גישה לנתוני שימוש. אם חסר אישור, הוא מפנה להגדרות. כשיש גישה, מחושבים טווחי שימוש ומוצגים בגרף לפי התקופה שבחר המשתמש.','בדיקת הרשאה, טעינת יום או שבוע ועדכון הגרף'),
('שאלה על היומן','המשתמש שולח שאלה. היישום קורא את היומן עבור 30 הימים האחרונים, מכין תיאור וממתין לתשובה. הצלחה מוסיפה הודעה לשיחה. אם נמצא תאריך מתאים, לחיצה עליו מציגה את היום במסך הבית. במקרה של כשל מוצגת הודעת חיבור.','שליחת שאלה, הכנת הנתונים, קבלת תשובה או כשל ומעבר לתאריך'),
('גיבוי ושחזור','בגיבוי בודקים שיש חשבון, מכינים עותק מקומי ומעלים אותו. בשחזור מבקשים אישור מהמשתמש, מורידים את הקובץ ובודקים את הגרסה. התאמה מאפשרת להחליף את המסד. אחרת הפעולה נעצרת ומוצגת הודעה.','שני מסלולים נפרדים לגיבוי ולשחזור, כולל נקודת בדיקת גרסת המסד')]:
 h(title);p(body);mark('תרשים זרימה של '+fig)
chapter(10);p('כשעבדתי על LifeTracker הבנתי שלזהות מיקום ולהבין מה עשיתי באותו רגע אלה שתי שאלות שונות. שתי מדידות קצת שונות יכולות להגיע מאותו חדר, ועצירה קצרה בדרך לא תמיד מצדיקה ביקור נפרד. העבודה על ציר הזמן עזרה לי להבין למה צריך לבדוק כל מדידה ביחס לפעילות שכבר התחילה. גם האפשרות לערוך ביקור קיבלה מבחינתי יותר משמעות, כי לפעמים המשתמש יודע להסביר את היום שלו טוב יותר מהטלפון.')
p('אם הייתי מתחיל מחדש, הייתי מכין כבר בהתחלה כמה מסלולי בדיקה קבועים, למשל הליכה עם עצירה באמצע וביקור שנמשך אחרי חצות. כך היה לי קל יותר להשוות בין מה שקרה בפועל לבין מה שמופיע ביומן אחרי כל שינוי בקוד.')
chapter(11)
chapter(12);p('הנספח מסודר לפי אותן קבוצות שמופיעות בפרק המימוש. לאחר קובצי Java מופיעים קובצי הממשק, המשאבים והתצורה של האפליקציה. אפשר לחפש במסמך שם של מחלקה או שם של פריסה כדי להגיע ישירות לקוד שלה.')
def listing(f,title=None):
 q=p(title or f.name,'Heading 3');q.paragraph_format.space_before=Pt(12);q=p(f.as_posix());q.paragraph_format.keep_with_next=True
 code(f.read_text(encoding='utf-8-sig').rstrip())
for g,names in groups:
 h(g)
 for n in names.split():listing(sources[n])
h('קובצי תצורה ובנייה')
for f in ['app/src/main/AndroidManifest.xml','settings.gradle.kts','build.gradle.kts','app/build.gradle.kts','gradle/libs.versions.toml','app/proguard-rules.pro']:listing(Path(f))
for folder in ['layout','menu','values','values-night','color','animator','raw','drawable']:
 h('משאבי '+folder)
 for f in sorted((Path('app/src/main/res')/folder).iterdir()):
  if f.suffix in ('.xml','.json'):listing(f)
d.core_properties.title='LifeTracker — תיק פרויקט';d.core_properties.subject='מערכות טלפונים ניידים';d.core_properties.author='LifeTracker'
out=Path('output/doc/LifeTracker_Project_Portfolio.docx');d.save(out)
check=Document(out);assert len([q for q in check.paragraphs if q.style.name=='Heading 1'])==len(chapters);assert zipfile.ZipFile(out).testzip() is None
print(out.resolve(),out.stat().st_size,len(check.paragraphs))


