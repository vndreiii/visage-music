import sys
from PIL import Image
# usage: edges.py IMG row|col FIXED LO HI [THRESH]  -> list of (pos, colour) where the colour jumps
im=Image.open(sys.argv[1]).convert('RGB')
mode=sys.argv[2]; fixed=int(sys.argv[3]); lo=int(sys.argv[4]); hi=int(sys.argv[5]); th=int(sys.argv[6]) if len(sys.argv)>6 else 18
prev=None;out=[]
for t in range(lo,hi):
    p=im.getpixel((t,fixed)) if mode=='row' else im.getpixel((fixed,t))
    if prev is not None and sum(abs(a-b) for a,b in zip(p,prev))>th: out.append((t,'#%02x%02x%02x'%p))
    prev=p
print(out)
