# Ledger for Android

The phone app for [Ledger](https://github.com/thorin29/ledger), the self-hosted
budget planner. It talks to your own Ledger server and nothing else (apart from
checking this repo for updates).

> **Status:** v0.22.2. Needs Ledger server 0.109.0 or newer.

## What it does

Four tabs along the bottom:

- **Today:** what's safe to send today, the bills balance (tap to update),
  what's free after the month, and the next bills due, each with a record
  button.
- **Month:** every item by half of the month, anything still owed from
  earlier, the item paid from what's left over, and income. Step back or
  forward a month.
- **Plan:** the bills balance walked forward 30–90 days, with the lowest
  point marked, and a slider showing what a payment today would do to it.
- **More:** what's waiting to send, a link to the web for year reports,
  appearance, updates and sign-out.

**Recording a payment** always takes a confirmation, as on the web: pick the
bill (due ones are offered first), check or type the amount on the number pad,
pick the date, then **Review** and **Confirm**.

**Bill reminders** (optional, More → Bill reminders): a notification around
8 AM the day a bill is due, or a day or two before. Bills already recorded are
skipped.

**Appearance:** light, dark or follow the phone, and one of six accent colors
(Cobalt by default), set per phone under More → Appearance.

**Works offline:** changes made without a connection wait on the phone and
send when it's back.
- Each change is applied once, even if it has to be sent twice.
- The server refuses a change if the same entry was changed elsewhere after
  you entered it. The app then lists it under "Not saved".

**Updates itself:** it checks this repo's releases every time the app opens,
when it comes back to the front (at most every 30 minutes), and about every
two hours in the background. When a newer version is out, a blue dot shows on
the More tab, App version shows "New", and a notification is posted once for
that version. The Update screen downloads and installs it.

## Adding a phone

1. On the web, open **Settings → Sign-in & people → Add a phone**. It shows a
   one-time code and a QR code, valid for 15 minutes.
2. In the app, tap **Scan QR code** and point it at the web page. It fills in
   the server address and code. Or type both. The scanner is built into the
   app (no Google Play services needed) and asks for the camera when you tap
   it; "Only this time" is enough.
3. Enter your Budget username and password.

The password is used once to get this phone its own token and is not kept.

## Security

- The token is encrypted with a key held in the Android Keystore, and is
  excluded from backups and device transfer (`allowBackup=false` plus
  data-extraction rules).
- The server address is typed in or scanned. It is never built into the app,
  this repo or the APK.
- The token is only sent to the configured server.
- A refused request is checked with the server before the phone gives up its
  sign-in, so one odd answer can't sign you out.
- Signing out removes the token, the cache and anything still waiting to send.

## Building

CI builds and signs the APK. See `RELEASING.md` for the signing secrets and how
to publish an update.

Locally, open the project in Android Studio. A debug build installs beside the
release app.

## Toolchain

AGP 9.4, Gradle 9.6, Kotlin 2.2, Compose BOM 2026.09, compileSdk/targetSdk 37,
minSdk 26, JDK 17. These are the same versions the Kairos app builds with. Pins
are in `gradle/libs.versions.toml`.

## License

MIT

## Credits

- [Figtree](https://github.com/erikdkennedy/figtree) font, SIL Open Font
  License 1.1 (`licenses/Figtree-OFL.txt`).
- [Phosphor Icons](https://phosphoricons.com), MIT
  (`licenses/Phosphor-MIT.txt`).
