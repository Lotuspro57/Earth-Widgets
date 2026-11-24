# Earth Orbit widgets

Home-screen widgets that show Earth on its real orbit around the Sun, placed by today's date.
Four shapes, and each widget can be **Solid**, **Glass** or **Transparent**.

| Shape | Size | What it shows |
|---|---|---|
| Orbit | 3x3, square | Whole orbit, month ring, Earth, Sun, date and distance |
| Globe | 2x2, round | Large Earth with real day and night, date and distance |
| Wide | 4x2 | Orbit on the left, date, season, distance and speed on the right |
| Strip | 4x1 | Small Earth, date, distance and season |

Looks: Solid (starry space), Glass (see-through dark tint with a soft edge), Transparent (no background, only Earth, Sun and text with a shadow). A switch hides the text.

## Build and install

1. Install Android Studio (Koala or newer) and open the `EarthOrbitWidget` folder. Let Gradle sync.
2. Connect your phone (USB debugging on) and press Run. Or use Build > Build APK(s) and copy `app/build/outputs/apk/debug/app-debug.apk` to the phone.
3. Open the app, tap **Add to home screen** on a shape. Or long-press the home screen, open Widgets and pick Earth Orbit.
4. Choose Solid, Glass or Transparent, then Save. To change it later, long-press the widget and use its reconfigure button (Android 12+ launchers).

Needs Android 8.0 (API 26) or newer. No internet and no extra libraries.

## How it works

- `OrbitMath.kt` computes Earth's position (Kepler orbit, eccentricity 0.0167) for any time.
- `EarthRenderer.kt` draws each widget as a picture: it shades Earth from two texture maps (surface colour; clouds, city lights and a land mask), then adds the orbit, Sun and text.
- The widget refreshes about every 30 minutes, on date or time-zone change, and when you tap it. Earth moves about 1 degree a day, so this uses very little battery. Earth's spin follows the real UTC clock.
- `tools/make_textures.py` and `tools/make_previews.py` (Python, numpy, Pillow) regenerate the texture maps and the widget picker previews. The textures use rough continent outlines, so coastlines are approximate.

## Settings per widget

Saved by widget id in `WidgetPrefs` (`Model.kt`). Each widget on your screen can have its own look.
