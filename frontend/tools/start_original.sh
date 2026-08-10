#!/usr/bin/env bash
# Idempotently start the ORIGINAL TWLan game (mariadb + event system + web on 127.0.0.1:8090).
# Requires the one-time root symlink /lib/twlan_db.sock -> TWLan-linux64/lib/twlan_db.sock (already created).
set -u
ROOT=/home/oscarwiklund/Projects/TWLAN2/TWLan-linux64
STEAMLIB=$HOME/.local/share/Steam/steamapps/common/SteamLinuxRuntime/steam-runtime/lib/x86_64-linux-gnu
PHPFLAGS=(-d openssl.cafile=/etc/pki/tls/certs/ca-bundle.crt -c "$ROOT/lib/php.ini" -d extension_dir="$ROOT/lib/php")
run() { LD_LIBRARY_PATH="$STEAMLIB" setsid nohup "$@" >/dev/null 2>&1 </dev/null & }

pgrep -f "$ROOT/lib/twlan_db.sock" >/dev/null || {
  (cd "$ROOT" && run ./bin/mysqld --defaults-file="$ROOT/lib/my.cnf" --basedir="$ROOT" --datadir="$ROOT/db" \
     --socket="$ROOT/lib/twlan_db.sock" --tmpdir="$ROOT/tmp" --log-error="$ROOT/tmp/db.log" --pid-file="$ROOT/lib/twlan_db.pid")
  sleep 4; }
pgrep -f "index.php -b ./htdocs" >/dev/null || { (cd "$ROOT/htdocs" && run "$ROOT/bin/php" "${PHPFLAGS[@]}" index.php -b ./htdocs); sleep 4; }
[ -f "$ROOT/htdocs/__router.php" ] || cat > "$ROOT/htdocs/__router.php" <<'PHP'
<?php
$uri = urldecode(parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH));
if ($uri !== '/' && file_exists(__DIR__ . $uri) && !is_dir(__DIR__ . $uri)) { return false; }
require __DIR__ . '/index.php';
PHP
pgrep -f "\-S 127.0.0.1:8090" >/dev/null || (cd "$ROOT/htdocs" && run "$ROOT/bin/php" "${PHPFLAGS[@]}" -S 127.0.0.1:8090 -t "$ROOT/htdocs" "$ROOT/htdocs/__router.php")
sleep 2
# (re)login: test account Nilsen / testpass1, world "Welt 1" (village id 2). Cookie jar: /tmp/twlan_cookies.txt
curl -s -c /tmp/twlan_cookies.txt -X POST http://127.0.0.1:8090/page/auth --data-urlencode "username=Nilsen" --data-urlencode "password=testpass1" -H "X-Requested-With: XMLHttpRequest" >/dev/null
curl -s -b /tmp/twlan_cookies.txt -c /tmp/twlan_cookies.txt -L http://127.0.0.1:8090/page/play/world >/dev/null
code=$(curl -s -o /dev/null -w '%{http_code}' -b /tmp/twlan_cookies.txt "http://127.0.0.1:8090/world/game.php?village=2&screen=overview")
echo "original game: game.php?screen=overview -> HTTP $code"
