#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <dmg-file> <volume-icon.icns>" >&2
  exit 2
fi

dmg_file=$1
icon_file=$2

if [[ ! -f "$dmg_file" || "$dmg_file" != *.dmg ]]; then
  echo "DMG does not exist: $dmg_file" >&2
  exit 2
fi
if [[ ! -f "$icon_file" || "$icon_file" != *.icns ]]; then
  echo "ICNS icon does not exist: $icon_file" >&2
  exit 2
fi

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/keystead-dmg-icon.XXXXXX")
mounted_device=""

cleanup() {
  if [[ -n "$mounted_device" ]]; then
    hdiutil detach "$mounted_device" >/dev/null 2>&1 || true
  fi
  rm -rf "$work_dir"
}
trap cleanup EXIT

hdiutil convert "$dmg_file" -format UDRW -o "$work_dir/writable.dmg" >/dev/null
attach_output=$(hdiutil attach -readwrite -nobrowse -noverify "$work_dir/writable.dmg")
mounted_device=$(printf '%s\n' "$attach_output" | awk -F '\t' '$3 ~ /^\/Volumes\// {gsub(/[[:space:]]/, "", $1); print $1; exit}')
mount_point=$(printf '%s\n' "$attach_output" | awk -F '\t' '$3 ~ /^\/Volumes\// {gsub(/^[[:space:]]+|[[:space:]]+$/, "", $3); print $3; exit}')

if [[ -z "$mounted_device" || -z "$mount_point" || ! -d "$mount_point" ]]; then
  echo "Could not locate the mounted DMG volume" >&2
  exit 1
fi

volume_icon="$mount_point/.VolumeIcon.icns"
if [[ -e "$volume_icon" ]]; then
  chmod u+w "$volume_icon"
fi
cp "$icon_file" "$volume_icon"
chmod a-w "$volume_icon"
xcrun SetFile -a C "$mount_point"
sync

hdiutil detach "$mounted_device" >/dev/null
mounted_device=""

hdiutil convert "$work_dir/writable.dmg" \
  -format UDZO \
  -imagekey zlib-level=9 \
  -o "$work_dir/fixed.dmg" \
  -ov >/dev/null
mv "$work_dir/fixed.dmg" "$dmg_file"
