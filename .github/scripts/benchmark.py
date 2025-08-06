import pytest
import os
import re
import ntpath
import json

pattern = r"bsl.+\-exec.jar"
thisPath = os.getcwd()
dirName = thisPath + "/build/libs"  

def test_analyze_ssl31(benchmark):
    benchmark(some_func, None)

def some_func(arg):
    pathToConfig = createBSLLSConfiguration()
    fullname = get_bslls_jar(dirName)
    cmdArgs = ['java']
    cmdArgs.append('-jar')
    cmdArgs.append(dirName + '/' + fullname)
    cmdArgs.append('-a')
    cmdArgs.append('-s')
    cmdArgs.append('ssl')
    cmdArgs.append('-r')
    cmdArgs.append('sarif')
    cmdArgs.append('-c')
    cmdArgs.append(pathToConfig)
    cmd = ' '.join(cmdArgs) 
    os.system(cmd)

def get_bslls_jar(dir):
    names = os.listdir(dir)
    for name in names:
        fullname = os.path.join(dir, name)
        if os.path.isfile(fullname) and re.search(pattern, fullname):
            return ntpath.basename(fullname)
    return None

def createBSLLSConfiguration():
    newPath = thisPath + "/ssl/.bsl-language-server.json"
    data = {}
    data['configurationRoot'] = './src'
    data['diagnostics'] = {}
    data['diagnostics']['mode'] = 'except'
    data['diagnostics']['parameters'] = {'Typo': False}

    with open(newPath, 'w') as outfile:
        json.dump(data, outfile)

    return newPath