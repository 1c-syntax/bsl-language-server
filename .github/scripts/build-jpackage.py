import ntpath
import os
import platform
import re
import shutil
import sys


def build_image(base_dir, image_prefix, executable_file):
    path_to_jar = get_bsl_ls_jar(base_dir)
    if path_to_jar is None:
        exit()

    cmd_args = [
        'jpackage',
        '--name', 'bsl-language-server',
        '--input', 'build/libs',
        '--main-jar', path_to_jar]

    if is_windows():
        cmd_args.append('--win-console')

    cmd_args.append('--type')
    cmd_args.append('app-image')
    cmd_args.append('--java-options')
    cmd_args.append('-Xmx2g')

    cmd = ' '.join(cmd_args)
    os.system(cmd)

    shutil.make_archive(
        "bsl-language-server_" + image_prefix,
        'zip',
        './',
        executable_file)


def is_windows():
    return platform.system() == 'Windows'


def get_base_dir():
    if is_windows():
        base_dir = os.getcwd() + "\\build\\libs"
    else:
        base_dir = os.getcwd() + "/build/libs"
    return base_dir


def get_bsl_ls_jar(dir_name):
    pattern = r"bsl.+\.jar"
    names = os.listdir(dir_name)
    for name in names:
        fullname = os.path.join(dir_name, name)
        if os.path.isfile(fullname) and re.search(pattern, fullname) and fullname.find('exec.jar') != -1:
            return ntpath.basename(fullname)

    return None


if __name__ == "__main__":
    # directory with build project
    arg_base_dir = get_base_dir()

    # image prefix: `win`, `nic` or `mac`
    arg_image_prefix = sys.argv[1]

    # executable file: `bsl-language-server` or `bsl-language-server.app`
    arg_executable_file = sys.argv[2]

    build_image(base_dir=get_base_dir(),
                image_prefix=sys.argv[1],
                executable_file=sys.argv[2])
