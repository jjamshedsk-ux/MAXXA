from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/com/maxxa/ai/MainActivity.kt').read_text(encoding='utf-8')
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle.kts').read_text(encoding='utf-8')
checks = {
    'Hindi command verbs': all(x in main for x in ['"खोलो"', '"चालू करो"', '"ऐप खोलो"']),
    'expanded common app aliases': all(x in main for x in ['"google maps"', '"gmail"', '"camera"', '"gallery"', '"settings"', '"play store"', '"telegram"']),
    'one-to-one fuzzy token matching': 'Greedy one-to-one token matching' in main and 'remaining.removeAt(bestIndex)' in main,
    'ambiguous match protection': 'ambiguityMargin' in main and 'minimumScore' in main and 'best.second - second < ambiguityMargin' in main,
    'lifecycle refresh': 'Lifecycle.Event.ON_RESUME' in main and 'LocalLifecycleOwner.current' in main and 'ACTION_PACKAGE_ADDED' in main and 'ACTION_PACKAGE_REMOVED' in main,
    'lifecycle compose dependency': 'lifecycle-runtime-compose:2.8.7' in gradle,
    'narrow launcher package visibility': 'android.intent.category.LAUNCHER' in manifest and '<queries>' in manifest,
    'no broad package visibility permission': 'QUERY_ALL_PACKAGES' not in manifest,
    'custom aliases bound to package IDs': 'customAliasesByPackage' in main and 'customAliasScore' in main,
    'launch failure returns false': '}.getOrDefault(false)' in main,
    'stale aliases cleaned after uninstall': 'installedPackages' in main and 'aliasPrefs.edit().remove(stale)' in main,
    'alias editor preloads saved values': 'aliasText = aliasPrefs.getString(app.packageName' in main,
}
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(checks.values()):
    raise SystemExit(1)
