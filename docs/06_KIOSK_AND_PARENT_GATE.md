# 06 · Tryb kiosku i bramka rodzica

## 1. Dlaczego Device Owner
| Funkcja | Bez Device Owner (screen pinning) | Z Device Owner |
|---|---|---|
| Wejście w Lock Task | systemowy dialog „Przypnij aplikację?” | automatyczne, bez dialogu |
| Wyjście | przytrzymanie Wstecz + Przegląd (dziecko trafi przypadkiem) | tylko z aplikacji (`stopLockTask()`) |
| Pasek powiadomień / Home / Recents | częściowo dostępne | zablokowane (`LOCK_TASK_FEATURE_NONE`) |
| Start po restarcie | nie | tak (aplikacja jako HOME) |
| Cicha aktualizacja APK | nie (dialog systemowy) | tak |

**Decyzja (ADR-003):** docelowo tablet jest dedykowany i provisionowany jako Device Owner. Tryb bez DO jest wspierany jako „degraded” z ostrzeżeniem w S11/S12.

## 2. Provisioning tabletu (instrukcja dla rodzica / dewelopera)
1. Przywróć ustawienia fabryczne. W kreatorze **nie dodawaj konta Google** (Device Owner wymaga braku kont na urządzeniu) — można połączyć się z Wi-Fi.
2. Ustawienia → Informacje o tablecie → 7× „Numer kompilacji” → Opcje programisty → **Debugowanie USB** włączone.
3. Zainstaluj APK release: `adb install familygames-x.y.z.apk`
4. Ustaw Device Ownera:
   ```bash
   adb shell dpm set-device-owner com.familygames/.kiosk.FamilyDeviceAdminReceiver
   ```
   Oczekiwany wynik: `Success: Device owner set to package com.familygames`.
5. Uruchom aplikację → kreator S12 (PIN, kod odzyskiwania) → aplikacja sama stosuje polityki (§3).
6. (Zalecane) Wyłącz debugowanie USB.

Alternatywa na przyszłość: provisioning QR przy pierwszym uruchomieniu po resecie (wymaga publicznego URL do APK i sumy kontrolnej) — poza zakresem v1.

Emulator do testów: obraz **bez** Google Play lub świeżo wyczyszczony, potem te same komendy.

Build **debug** ma `applicationIdSuffix = ".debug"`, więc komponent podaje się w pełnej formie:
```bash
adb shell dpm set-device-owner com.familygames.debug/com.familygames.kiosk.FamilyDeviceAdminReceiver
```
(i włącz kiosk w debug tymczasowo, np. wariantem `kioskDebug` albo nadpisaniem `KIOSK_ENABLED`). Usunięcie DO na emulatorze: `adb shell dpm remove-active-admin <ten sam komponent>` (działa tylko dla aplikacji z `android:testOnly="true"`) albo wipe emulatora.

## 3. `KioskManager` — polityki stosowane przy każdym starcie (gdy `isDeviceOwner && KIOSK_ENABLED`)
```kotlin
class KioskManager(private val ctx: Context) {
    private val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(ctx, FamilyDeviceAdminReceiver::class.java)
    val isDeviceOwner get() = dpm.isDeviceOwnerApp(ctx.packageName)

    fun applyPolicies() {
        if (!isDeviceOwner) return
        dpm.setLockTaskPackages(admin, arrayOf(ctx.packageName))
        dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
        val home = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME); addCategory(Intent.CATEGORY_DEFAULT) }
        dpm.addPersistentPreferredActivity(admin, home, ComponentName(ctx, MainActivity::class.java))
        KIOSK_RESTRICTIONS.forEach { dpm.addUserRestriction(admin, it) }
        // KIOSK_RESTRICTIONS = listOf(DISALLOW_SAFE_BOOT, DISALLOW_FACTORY_RESET, DISALLOW_ADD_USER)
        // Uwaga: NIE dodawaj DISALLOW_INSTALL_UNKNOWN_SOURCES bez testu — zweryfikuj, że nie blokuje własnego updatera.
        dpm.setStatusBarDisabled(admin, true)
        dpm.setSystemUpdatePolicy(admin, SystemUpdatePolicy.createWindowedInstallPolicy(120, 300)) // aktualizacje OS 2:00–5:00
    }

    fun enterLockTask(activity: Activity) {
        val am = ctx.getSystemService(ActivityManager::class.java)
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) activity.startLockTask()
    }

    fun exitKiosk(activity: Activity) {               // tylko po PIN
        activity.stopLockTask()
        dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName)
        dpm.setStatusBarDisabled(admin, false)
        activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun decommission() {                               // „Usuń uprawnienia Device Owner” (PIN + potwierdzenie)
        dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName)
        dpm.setStatusBarDisabled(admin, false)
        KIOSK_RESTRICTIONS.forEach { dpm.clearUserRestriction(admin, it) }   // ta sama lista co w applyPolicies()
        @Suppress("DEPRECATION") dpm.clearDeviceOwnerApp(ctx.packageName)
    }
}
```
- Manifest `MainActivity`: `android:launchMode="singleTask"`, `android:lockTaskMode="if_whitelisted"`, intent-filtry `MAIN/LAUNCHER` **oraz** `MAIN/HOME/DEFAULT` (gotowy fragment: `config/android/AndroidManifest.snippet.xml`).
- `MainActivity.onResume()` → `kiosk.enterLockTask(this)` (gdy `KIOSK_ENABLED`). Bez DO wywołanie pokaże systemowy dialog przypinania — tylko jeśli rodzic świadomie wybrał tryb „bez kiosku + przypinanie”.
- `LOCK_TASK_FEATURE_NONE` ukrywa Home, Recents, powiadomienia, menu zasilania (globalne akcje). Przycisk zasilania nadal wygasza ekran; wymuszony restart (długie przytrzymanie) uruchomi aplikację ponownie jako HOME.
- Rozważ (Q3 w docs/11): `setKeyguardDisabled(admin, true)` — brak ekranu blokady po wybudzeniu (wygoda) vs. bezpieczeństwo.

## 4. Bramka rodzica — `ParentGateDialog`

### Wejście
- S01: `HoldToActivateButton` (kłódka) — 3000 ms przytrzymania.
- `ParentGateDialog(purpose: GatePurpose)` gdzie `GatePurpose = EnterParentZone | Critical(action)`.

### Zadanie (domyślne dla `EnterParentZone`)
```kotlin
class ParentChallengeGenerator(private val random: Random) {
    fun next(): Challenge {
        val a = random.nextInt(3, 10); val b = random.nextInt(3, 10); val ans = a * b
        val distractors = linkedSetOf(a * (b + 1), a * (b - 1), (a + 1) * b, ans + 2, ans - 2).filter { it > 0 && it != ans }.shuffled(random).take(3)
        return Challenge(text = "$a × $b = ?", options = (distractors + ans).shuffled(random), correct = ans)
    }
}
```
### PIN (wymagany dla `Critical`: wyjście z kiosku, zmiana PIN, usunięcie DO, wyczyszczenie postępów)
- 4 cyfry, ustawiany w S12. Klawiatura numeryczna 3×4 w dialogu (przyciski 72 dp).
- Hash: `PBKDF2WithHmacSHA256`, 120 000 iteracji, sól 16 B (`SecureRandom`), wynik 32 B; Base64 w DataStore. Porównanie `MessageDigest.isEqual`.
- Opcja w S11: „Zawsze wymagaj PIN” (zamiast zadania także przy wejściu do strefy rodzica).

### Ochrona przed zgadywaniem — `GateLockoutPolicy`
- 3 błędy → blokada 60 s; każda kolejna seria 3 błędów podwaja czas (max 15 min). `gate_lockout_until` w DataStore (przetrwa restart).
- Brak interakcji 30 s → dialog zamyka się sam.
- Udane wejście zeruje licznik.

### Zapomniany PIN
W S12 po ustawieniu PIN pokazywany jest **jednorazowo** 12-znakowy kod odzyskiwania (format `XXXX-XXXX-XXXX`, alfabet bez 0/O/1/I), z prośbą o zapisanie. Przechowywany tylko jako hash. W dialogu PIN link „Nie pamiętam PIN” → wpisanie kodu → ustawienie nowego PIN → wygenerowanie nowego kodu.

## 5. Sesja rodzica a Lock Task (Photo Picker i aparat)
W Lock Task system blokuje aktywności z pakietów spoza listy `setLockTaskPackages`. Photo Picker (`PickVisualMedia`) i aparat (`TakePicture`) to inne pakiety, więc **bez obsługi nie zadziałają w kiosku**.

Rozwiązanie podstawowe — dynamiczna biała lista na czas sesji rodzica:
```kotlin
fun beginParentSession() {
    if (!isDeviceOwner) return
    val pm = ctx.packageManager
    val extra = listOf(
        Intent(MediaStore.ACTION_PICK_IMAGES),                         // API 33+ / backport
        Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*"),         // fallback pickera na starszych
        Intent(MediaStore.ACTION_IMAGE_CAPTURE),
    ).mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
    dpm.setLockTaskPackages(admin, (listOf(ctx.packageName) + extra).distinct().toTypedArray())
}
fun endParentSession() { if (isDeviceOwner) dpm.setLockTaskPackages(admin, arrayOf(ctx.packageName)) }
```
- `endParentSession()` wywoływane przy wyjściu z `ParentGraph`, po 5 min bezczynności w strefie rodzica (auto-powrót do S01) oraz przy każdym starcie aplikacji (na wypadek crasha).
- Fallback, jeśli na konkretnym tablecie resolve zwraca „chooser”/brak pakietu: `stopLockTask()` na czas sesji rodzica + ten sam timeout 5 min → `startLockTask()`. Zachowanie zweryfikować na docelowym tablecie (checklista w docs/10).
- Alternatywa bez zewnętrznego aparatu: CameraX w aplikacji (dodatkowa zależność — tylko jeśli fallback okaże się problematyczny; ADR).

## 6. Immersive mode
```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
WindowInsetsControllerCompat(window, window.decorView).apply {
    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    hide(WindowInsetsCompat.Type.systemBars())
}
```
Ponowne ukrycie w `onWindowFocusChanged(hasFocus = true)`. W strefie rodzica paski mogą zostać ukryte (spójność).

## 7. Odbiorniki systemowe
- `FamilyDeviceAdminReceiver : DeviceAdminReceiver` — `onEnabled` → `applyPolicies()`; `onLockTaskModeEntering/Exiting` → log debug.
- `BootCompletedReceiver` nie jest potrzebny (aplikacja jest HOME), ale `MY_PACKAGE_REPLACED` jest (docs/07).
