# Graffiti Pad

A small always-on-top Windows pad where you write Palm Pilot Graffiti strokes with the mouse,
pen or touch screen. Each stroke is recognized with the **$1 Unistroke Recognizer** and turned
into a real keystroke in whatever app you were typing in.

Written in pure Kotlin with Compose Multiplatform (Desktop). No native code.

Built with [Claude Code](https://claude.com/claude-code), Anthropic's AI coding assistant.

## Run it

You need a JDK, version 17 or newer, on Windows (for example Temurin 17 or 21).

```
gradlew.bat run
```

### Portable build (no install)

```
gradlew.bat packagePortable
```

This makes `build\portable\GraffitiPad-1.0.0-portable.zip` (about 57 MB). It includes its own
Java runtime, so the PC it runs on doesn't need Java. Unzip it anywhere, such as a USB stick, a
network share or the desktop, and run `GraffitiPad\GraffitiPad.exe`. Nothing is installed and no
admin rights are needed. Keep the whole `GraffitiPad` folder together: the exe needs the `app`
and `runtime` folders next to it.

### Single exe

```
gradlew.bat packageSingleExe
```

This makes one file, `build\single\GraffitiPad.exe` (about 57 MB), with the portable app packed
inside it. Copy just that file anywhere and run it. The first time a build runs on a PC, it
unpacks itself to `%LOCALAPPDATA%\GraffitiPad\` (about 120 MB, a couple of seconds) and starts
from there. Later runs start straight away. When a newer build runs, it unpacks fresh and
removes the older copy.

The launcher is `src\launcher\Launcher.cs`, compiled with the C# compiler that ships with
Windows (.NET Framework 4), so building it needs no extra tools.

### Installer

To build an installer instead:

```
gradlew.bat packageMsi      (or packageExe)
```

The installer ends up in `build\compose\binaries\main\msi\`. The first build downloads Gradle,
Compose and the WiX toolset, so it takes a few minutes.

To run the recognizer tests:

```
gradlew.bat test
```

## How to use it

1. Click into the app you want to type into, such as Notepad or a browser.
2. Write on the pad. The pad never takes keyboard focus, so the keys go to that app.
3. Write letters on the left (**abc**) and digits on the right (**123**), like on a Palm.
4. Start each stroke at the orange dot shown in the guide. The direction you draw in matters.

| Stroke | Does |
|---|---|
| line to the right | Space |
| line to the left | Backspace |
| diagonal from top-right to bottom-left | Enter |
| line upward | Shift. Do it twice for Caps Lock, and a third time to turn it off. |
| tap | Punctuation. The next stroke is read as punctuation (`, ? ! - ' ( ) /`), and a second tap types `.` |

- **The guide is also an on-screen keyboard.** Tap any shape in it to type that character, the same as if you had drawn it. Shift and Caps Lock still apply. Punctuation keys type right away, and the extra **tap** key does the same as tapping the pad: once for punctuation mode, twice for `.`. To scroll the guide, use the mouse wheel or the scrollbar, or drag it up and down with a pen or finger.
- **⌫ Back**, **⌦ Del** (forward delete) and **↵ Return** next to the typed-text strip are always visible, even when the guide is hidden or scrolled. In punctuation mode, Back cancels punctuation mode instead of deleting, just like the backspace stroke.
- **SHIFT, CAPS and PUNCT** in the header show the current mode, and you can click them. Each one turns its mode on, or back off if it was already on. SHIFT applies to the next letter only, and CAPS stays on until you turn it off.
- **Keys: on/off** turns sending keystrokes on or off. With it off, you can practise; the strip under the pad still shows what you wrote.
- **Teach** lets you add your own examples. Pick a shape in the guide, then draw it your way three or four times. Your examples are added next to the built-in ones and saved in `%APPDATA%\GraffitiPad\templates.txt`. **Forget mine** removes them.

## How recognition works

`Recognizer.kt` implements $1 as described in the paper (Wobbrock, Wilson & Li, UIST 2007):

1. Resample the stroke to 64 evenly spaced points.
2. Scale it, then move its centroid to the origin.
3. Compare it point by point with every template. A golden-section search tries small rotations to find the best fit.
4. The template with the smallest average distance wins. Its score runs from 0 to 1, and a score below 0.75 is rejected (the pad shows `?`).

Graffiti needs three changes from the original paper:

- **No rotation normalization.** Rotation is only searched within ±20°. Plain $1 rotates every stroke to a standard angle, which would make a down stroke (I) match an up stroke (Shift).
- **Uniform scaling.** Plain $1 stretches each stroke into a square. That breaks straight lines and loses the difference between tall and wide shapes.
- **Straight strokes are matched by direction.** When a stroke is almost straight, it is compared by its angle with the line gestures (space, backspace, enter, shift, I, 1 and the punctuation lines).

Where each stroke can be written:

- In the letter area, the recognizer only looks at letters and the special strokes.
- In the number area, it only looks at digits and the special strokes.
- After a tap, it only looks at punctuation.

## Test results

The test used computer-generated strokes, not real handwriting. Each built-in shape was distorted 200 times:

- random stretching and shearing
- rotation up to ±12°
- wobble
- uneven pen speed

Results:

- **Moderate distortion:** 98.3% correct.
- **Heavy distortion:** 93.4% correct.

The most common mix-ups were between shapes that really are close:

- u and v
- p, d, b and r
- `)` and `?`

## Limits

- **The shapes are a best effort.** The built-in shapes follow the original Graffiti alphabet as closely as I could reproduce it, but some may not be exact. X and Y in particular are approximations. If a letter doesn't match how you remember writing it, use **Teach**: a few examples are enough.
- **US keyboard layout assumed.** Keystrokes are sent with `java.awt.Robot`, using US key codes. On other layouts, punctuation may come out as different characters.
- **Admin windows don't receive keys.** Windows blocks keystrokes into apps running as administrator unless Graffiti Pad also runs as administrator.
- **One stroke per character.** $1 recognizes single strokes only, so characters need to be written in one stroke, as in classic Graffiti.

## Files

- `src/main/kotlin/graffiti/`
  - `Recognizer.kt`: the $1 recognizer
  - `Alphabet.kt`: the Graffiti shapes as unit-box paths
  - `Interpreter.kt`: Shift, Caps Lock and punctuation modes, and the template library with saved user examples
  - `KeySender.kt`: turns characters into keystrokes
  - `Main.kt`: the Compose window, writing pad and guide
- `src/test/kotlin/graffiti/RecognizerTest.kt`
