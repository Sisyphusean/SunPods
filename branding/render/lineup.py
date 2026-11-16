"""Render the live bud with given params and paste it next to a reference image.
Usage: python lineup.py <ref.png> <out.jpg> '<params json>'"""
import json, os, sys
sys.path.insert(0, r"C:\Users\peyio\Documents\Tools\blender-mcp")
from bmcp import call
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ref, out, params = sys.argv[1], sys.argv[2], json.loads(sys.argv[3])
code = "PARAMS = " + repr(params) + "\n" + open(os.path.join(HERE, "live_build.py"), encoding="utf-8").read()
r = call("execute_code", {"code": code})
if r.get("status") != "success":
    print(json.dumps(r)[:1500]); sys.exit(1)
a = Image.open(ref).convert("RGB").resize((560, 560))
b = Image.open(os.path.join(HERE, params.get("out", "live.png"))).convert("RGB").resize((560, 560))
o = Image.new("RGB", (1130, 560)); o.paste(a, (0, 0)); o.paste(b, (570, 0)); o.save(os.path.join(HERE, out), quality=88)
print("ok")
