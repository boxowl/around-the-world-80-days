#!/bin/zsh
set -eu
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
avd_name=Expedition_API_36
if [[ ! -x "$ANDROID_HOME/emulator/emulator" ]]; then
  print 'Android Emulator не установлен. См. docs/EMULATOR.md.'
  exit 1
fi
exec "$ANDROID_HOME/emulator/emulator" -avd "$avd_name" -no-boot-anim
