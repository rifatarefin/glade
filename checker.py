import subprocess
p = subprocess.Popen(['matlab', '-nodesktop', '-nosplash', "-r 'validitychecker('fuzz');quit();'"], stdout=subprocess.PIPE, shell=False)
