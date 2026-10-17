# PROGRESS — লিমিটের পর এখান থেকে শুরু করো (কিছু আবার লিখো না)

## সম্পূর্ণ (লেখা ও যাচাই)
- Gradle/CI: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `.github/workflows/build.yml`, `app/debug.keystore` (স্থির সাইনিং চাবি)
- Manifest + রিসোর্স: `AndroidManifest.xml`, `values/`, `xml/accessibility_service_config.xml`, আইকন
- `core/`: Prefs, BushraState, BushraCore (এজেন্ট লুপ, TTS)
- `ai/`: GeminiClient (মডেল ফলব্যাকসহ), Tools (৩৪টি টুল)
- `actions/ActionExecutor.kt`: সব টুলের বাস্তবায়ন
- `service/`: BushraService (ওয়েক ওয়ার্ড + ইনকামিং কল), BushraAccessibilityService, CallReceiver
- `ui/`: Clay (ক্লেমরফিজম সিস্টেম), HomeScreen, PermissionsScreen, SettingsScreen, Perms
- `MainActivity.kt`

## যাচাইয়ের অবস্থা
- নন-UI কোড (`core`, `ai`, `actions`, `service`): kotlinc 1.9.24 + android.jar (API 34) দিয়ে কম্পাইল পাস (exit 0), শুধু deprecation warning।
- UI কোড (Compose) ও Gradle বিল্ড: এই সার্ভারে যাচাই করা যায়নি (Maven বন্ধ)। প্রথম যাচাই হবে GitHub Actions-এ।

## পরের ধাপ (ক্রমানুসারে)
1. কোড GitHub রিপোতে তোলো → Actions ট্যাবে "Build APK" চলবে।
2. বিল্ড ব্যর্থ হলে লগের লাল অংশ কপি করে দাও → শুধু ওই ফাইলের ত্রুটি ঠিক করো।
3. APK Releases পেজ (latest) থেকে নামিয়ে ইনস্টল।
4. ফোনে পরীক্ষা: পারমিশন পেজ → Accessibility (Restricted settings সহ) → API key → "চালু করো"।
5. পরীক্ষার পর যা ঠিক করতে হতে পারে: WhatsApp বাটনের নাম (ভাষাভেদে), ওয়েক ওয়ার্ডের বানান (সেটিংসে বদলানো যায়), ফোনভেদে ব্যাকগ্রাউন্ড বন্ধ হওয়া।

## এখনও নেই (ইচ্ছে হলে পরে)
- লাইভ স্ক্রিন শেয়ারিং (MediaProjection) — বড় কাজ, আলাদা ধাপে
- নিজের লোগো আইকন (এখন সাধারণ গোলক আইকন; `res/drawable/ic_fg.xml` বদলালেই হবে)
- নোটিফিকেশন পড়া (NotificationListenerService)
