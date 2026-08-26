# unity-X Android

Üç uygulama:

| Modül | applicationId | Kim |
| --- | --- | --- |
| `:consumer` | `com.nexusneuro.consumer` | Son kullanıcı (e-posta + Supabase) |
| `:wear` | `com.nexusneuro.consumer` | Saat (consumer ile Data Layer) |
| `:app` | `com.nexusneuro.app` | Personel / Administrator (yerel giriş) |

## Gereksinimler

`android/local.properties` içinde (SDK satırına ek):

```properties
SUPABASE_URL=https://mpnrtyzxfeeqbzdrfnti.supabase.co
SUPABASE_ANON_KEY=<anon_key>
```

Geliştirmede e-posta onayı kapalı olsun: Supabase Dashboard → Authentication → Providers → Email → **Confirm email** off.

### Doğrulama linki localhost:3000 açıyorsa

Supabase varsayılan Site URL `http://localhost:3000`. Android için değiştir:

1. [URL Configuration](https://supabase.com/dashboard/project/mpnrtyzxfeeqbzdrfnti/auth/url-configuration)
2. **Site URL:** `com.nexusneuro.consumer://login-callback`
3. **Redirect URLs** listesine ekle: `com.nexusneuro.consumer://login-callback`
4. Kaydet; yeni kayıt maili artık uygulamayı açar (consumer APK kurulu olmalı).

En kolayı geliştirmede: Email → **Confirm email** kapat → mail linkine gerek kalmaz.

## Son kullanıcı (önerilen)

```bash
cd android
.\gradlew.bat :consumer:assembleDebug
.\gradlew.bat :wear:assembleDebug
```

| APK | Yol |
| --- | --- |
| Consumer | `consumer/build/outputs/apk/debug/consumer-debug.apk` |
| Wear | `wear/build/outputs/apk/debug/wear-debug.apk` |

1. Telefona **consumer** kur; saate **wear** kur.
2. Telefonda e-posta ile kayıt / giriş.
3. İlk cihaz otomatik **ana cihaz** olur → saatte **BAŞLAT**.
4. İkinci telefonda aynı hesap: buluttan nabız görür; **BU CİHAZI ANA YAP** (eski ana 2 dk sessizse veya orada çıkış).

## Personel / Admin

```bash
.\gradlew.bat :app:assembleDebug
```

| Role | Kimlik | Şifre | Ekran |
| --- | --- | --- | --- |
| Administrator | `57019027696` | `15041212.k` | Tam EEG / REM dashboard |
| Personel / Sunum | `5433307329` / `159951` | ilgili şifre | Personel bilgi ekranı (son kullanıcı → consumer) |

## Wear notları

- Nabız: Health Services `MeasureClient`.
- SpO₂: `PassiveMonitoringClient` — saatte yoksa **“SpO₂ bu saatte yok”** (yanlış ölçüm yok).
- Saat ↔ telefon yalnız **consumer** `applicationId` ile eşleşir.

## Notlar

- Ana cihaz heartbeat 30 sn (`touch_device`); stale claim 2 dk.
- Anon key yalnız `local.properties` / BuildConfig; git’e koyma.
- USB Arduino stim hâlâ stub (`:app`).
