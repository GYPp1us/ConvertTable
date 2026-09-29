from pathlib import Path
from zipfile import ZipFile
import re,sys
p=Path(sys.argv[1]).resolve()
assert p.is_file() and 'ui_v1.4' in p.parts
with ZipFile(p) as z:
 entries=[(info,z.read(info.filename)) for info in z.infolist()]
changed=0
for i,(info,data) in enumerate(entries):
 if info.filename.startswith('xl/tables/') and b'AdvancedRecipesV11' in data:
  if b'name="XP"' in data:
   data=data.replace(b'name="XP"','name="死亡次数"'.encode('utf-8'))
   entries[i]=(info,data);changed+=1
assert changed==1,changed
tmp=p.with_suffix('.metadata-fix.xlsx')
with ZipFile(tmp,'w') as z:
 for info,data in entries:z.writestr(info,data)
tmp.replace(p)
print('Corrected the affected table column metadata; all other ZIP entries unchanged.')

