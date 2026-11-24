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

