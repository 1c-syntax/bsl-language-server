import ntpath
import os
import platform
import re
import shutil
import sys

# POSIX (Linux/macOS) launcher wrapper. Lets the user tune JVM options
# (for example, the heap size via -Xmx) without editing the bundled .cfg.
# The value of BSL_LANGUAGE_SERVER_OPTS is forwarded to the native jpackage
# launcher through _JAVA_OPTIONS, which the JVM applies after the options
# baked into the .cfg, so it overrides the built-in -Xmx default.
WRAPPER_SH = """#!/bin/sh
# Launcher wrapper for BSL Language Server.
# Set BSL_LANGUAGE_SERVER_OPTS to pass extra JVM options, e.g.:
#   BSL_LANGUAGE_SERVER_OPTS="-Xmx8g" ./bsl-language-server.sh
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ -n "${BSL_LANGUAGE_SERVER_OPTS:-}" ]; then
  if [ -n "${_JAVA_OPTIONS:-}" ]; then
    export _JAVA_OPTIONS="$_JAVA_OPTIONS $BSL_LANGUAGE_SERVER_OPTS"
  else
    export _JAVA_OPTIONS="$BSL_LANGUAGE_SERVER_OPTS"
  fi
fi

exec "$SCRIPT_DIR/bsl-language-server" "$@"
"""

# Windows launcher wrapper, same behaviour as WRAPPER_SH.
WRAPPER_CMD = """@echo off
rem Launcher wrapper for BSL Language Server.
rem Set BSL_LANGUAGE_SERVER_OPTS to pass extra JVM options, e.g.:
rem   set BSL_LANGUAGE_SERVER_OPTS=-Xmx8g
rem   bsl-language-server.cmd
setlocal
if defined BSL_LANGUAGE_SERVER_OPTS (
  if defined _JAVA_OPTIONS (
    set "_JAVA_OPTIONS=%_JAVA_OPTIONS% %BSL_LANGUAGE_SERVER_OPTS%"
  ) else (
    set "_JAVA_OPTIONS=%BSL_LANGUAGE_SERVER_OPTS%"
  )
)
"%~dp0bsl-language-server.exe" %*
"""


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
    cmd_args.append('-Xmx4g')

    cmd = ' '.join(cmd_args)
    os.system(cmd)

    write_launcher_wrapper(image_prefix)

    shutil.make_archive(
        "bsl-language-server_" + image_prefix,
        'zip',
        './',
        executable_file)


def write_launcher_wrapper(image_prefix):
    if image_prefix == 'win':
        wrapper_path = os.path.join(
            'bsl-language-server', 'bsl-language-server.cmd')
        content = WRAPPER_CMD
    elif image_prefix == 'mac':
        wrapper_path = os.path.join(
            'bsl-language-server.app', 'Contents', 'MacOS',
            'bsl-language-server.sh')
        content = WRAPPER_SH
    else:
        wrapper_path = os.path.join(
            'bsl-language-server', 'bin', 'bsl-language-server.sh')
        content = WRAPPER_SH

    with open(wrapper_path, 'w') as wrapper_file:
        wrapper_file.write(content)

    if image_prefix != 'win':
        os.chmod(wrapper_path, 0o755)


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
