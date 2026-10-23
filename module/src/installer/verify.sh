abort_verify() {
  ui_print '*********************************************************'
  ui_print "! $1"
  ui_print '! This zip may be corrupted, please try downloading again'
  abort '*********************************************************'
}

verify_file() {
  local zip="$1" name="$2" path="$3" expected
  [ -f "$path" ] || abort_verify "$name not exists"
  expected=$(unzip -p "$zip" "$name.sha256sum") || abort_verify "$name.sha256sum not exists"
  [ "${#expected}" -eq 64 ] || abort_verify "Invalid hash for $name"
  case "$expected" in *[!0-9a-f]*) abort_verify "Invalid hash for $name";; esac
  printf '%s  %s\n' "$expected" "$path" | sha256sum -c -s - || abort_verify "Failed to verify $name"
  ui_print "- Verified $name"
}

extract() {
  local zip="$1" name="$2" dir="$3" junk_paths="${4:-false}" path="$3/$2" opts=-o
  if [ "$junk_paths" = true ]; then
    opts=-oj
    path="$dir/$(basename "$name")"
  fi
  unzip "$opts" "$zip" "$name" -d "$dir" >&2 || abort_verify "Cannot extract $name"
  verify_file "$zip" "$name" "$path"
}
