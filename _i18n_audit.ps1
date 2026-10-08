$files = @(
  "C:\Users\jx\Desktop\icu.apk\icu.apk\app\src\main\assets\lanhu\v2\js\screens.js",
  "C:\Users\jx\Desktop\icu.apk\icu.apk\app\src\main\assets\lanhu\v2\js\core.js",
  "C:\Users\jx\Desktop\icu.apk\icu.apk\app\src\main\assets\lanhu\v2\icu-native-bridge.js"
)
$han = '[一-鿿]'
foreach ($f in $files) {
  $lines = Get-Content $f -Encoding UTF8
  $n = 0
  foreach ($l in $lines) {
    $t = $l.Trim()
    if ($t -notmatch $han) { continue }
    if ($t.StartsWith('/*') -or $t.StartsWith('*') -or $t.StartsWith('//')) { continue }
    if ($l -match 'T\(') { continue }
    $n++
  }
  Write-Output ("{0}: {1}" -f (Split-Path $f -Leaf), $n)
}
