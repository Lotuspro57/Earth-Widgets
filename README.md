# Earth Orbit widgets

Home-screen widgets that show Earth on its real orbit around the Sun, placed by today's date.
Four shapes, and each widget can be **Solid**, **Glass** or **Transparent**.

| Shape | Default size | What it shows |
|---|---|---|
| Orbit | 2x2 | Whole orbit with Earth and Sun. Month ring when the widget is large |
| Globe | 1x1, round | Earth with real day and night. Date text appears when resized larger |
| Wide | 3x1 | Orbit on the left, date and numbers on the right |
| Strip | 2x1 | Small Earth, date and distance |

All shapes can be resized. Very small widgets hide the text so Earth stays clear.

Looks: Solid (starry space), Glass (see-through dark tint), Transparent (no background, only Earth, Sun and text with a shadow).

Per widget settings: look, date (today live, or any date you pick), show or hide details, and a time-lapse animation.

Timestamp: a live clock (HH:mm) sits in the corner of a live widget. Time-lapse frames of the Globe and Strip draw the time of day in the text.

Time-lapse: the widget holds 30 pictures and the home screen flips through them about 8 times a second. Orbit and Wide loop through one year (Earth circles the Sun). Globe and Strip loop through one day (Earth spins). Some launchers do not auto-flip widget pictures. If yours does not, use the in-app viewer, which always animates.

In the app: a live Earth with the full timestamp (with seconds), Pick date (day, month, year), step buttons for -1/+1 year, month and day, Play with 1 day, 1 week or 1 month per second, and Now.

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
