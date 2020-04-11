import pytest
import os
import re
import ntpath

pattern = r"bsl.+\.jar"
dirName = os.getcwd() + "/build/libs"  

def test_analyze_ssl31(benchmark):
    benchmark(some_func, None)

def some_func(arg):
    fullname = get_bslls_jar(dirName)
    cmdArgs = ['java']
    cmdArgs.append('-jar')
    cmdArgs.append(dirName + '/' + fullname)
    cmdArgs.append('-a')
    cmdArgs.append('-s')
    cmdArgs.append('ssl')
    cmd = ' '.join(cmdArgs) 
    os.system(cmd)

def get_bslls_jar(dir):
    names = os.listdir(dir)
    for name in names:
        fullname = os.path.join(dir, name)
        if os.path.isfile(fullname) and re.search(pattern, fullname) and fullname.find('sources.jar') == -1 and fullname.find('javadoc.jar') == -1:
            return ntpath.basename(fullname)
    return None
