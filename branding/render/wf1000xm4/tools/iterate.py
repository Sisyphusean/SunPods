# One review iteration (tools only): refit the review angles from the last fits, the board, and the
# close-up boards (body and microphone; tip, slit and print).   python iterate.py <name>
# (X4_OVR in the environment tries shape values without editing wf1000xm4.js)
import os, sys, subprocess, time
HERE = os.path.dirname(os.path.abspath(__file__))
X4 = os.path.normpath(os.path.join(HERE, '..'))
name = sys.argv[1]
it, cu = os.path.join(X4, '_fit', name), os.path.join(X4, '_fit', name, 'closeup')
t0 = time.time()
run = lambda *a: subprocess.run(list(a), cwd=HERE, check=True, stdout=subprocess.DEVNULL if a[0] != 'node' or 'board.mjs' not in a[1] else None)
run('node', 'board.mjs', it)
run(sys.executable, 'compose_board.py', it)
run('node', 'closeup.mjs', cu)
run(sys.executable, 'closeup.py', cu, os.path.join(it, 'closeup_body.jpg'), name, 'body,boss')
run(sys.executable, 'closeup.py', cu, os.path.join(it, 'closeup_detail.jpg'), name, 'tip,slit,logo')
print(f'{time.time() - t0:.0f}s', it)
