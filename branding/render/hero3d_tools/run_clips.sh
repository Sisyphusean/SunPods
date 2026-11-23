# Renders the onboarding clips in Platinum Silver and Smoky Pink (frames in anim/colours/<clip>_<colour>/),
# then packs them exactly like the black originals (anim/pack.py, 480 px, every frame).
B="/c/Program Files/Blender Foundation/Blender 3.2/blender.exe"
R=/c/Users/peyio/Documents/SonyPodsBattery/branding/render
RAW=/c/Users/peyio/Documents/SonyPodsBattery/app/src/main/res/raw
cd $R
for clip in open pair; do
  for c in silver pink; do
    out="$R/anim/colours/${clip}_${c}"
    if [ "$clip" = open ]; then export HERO3D_HIDE_INTERNALS=1; else export HERO3D_HIDE_INTERNALS=0; fi
    "$B" -b anim/xm5_case_anim.blend --python hero3d_tools/render_clip.py -- ${c}_clip $clip "$(cygpath -w $out)" 2>&1 | grep -E "FINISH|INTERNALS|RENDERED|Error|Traceback"
    n=$(ls $out/*.png | wc -l); echo "$clip $c frames $n"
    python anim/pack.py "$(cygpath -w $out)" "$(cygpath -w $R/anim/colours/anim_${clip}_${c}.webp)" 480 1
    cp "$R/anim/colours/anim_${clip}_${c}.webp" "$RAW/anim_${clip}_${c}.webp"
  done
done
echo CLIPS_DONE
