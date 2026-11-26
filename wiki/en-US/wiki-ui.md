[Home](../wiki-language.md) > [Documentation](wiki-main.md) > UI API

# 🖥️ UI API

The `com.avrix.api.ui` package integrates [LixUI](https://github.com/Brov3r/LixUI) into the
Project Zomboid client. The overlay is drawn over the finished game frame and captures mouse and
keyboard input only while a widget is under the pointer or holds focus.

The API is client-only. Calls on a dedicated server are ignored.

---

### 🚀 Quick Start

Create a LixUI widget and register it through `AvrixUi`. The method can be called from any thread.

```java
Button button = new Button("Hello");
button.setPosition(24, 24);
button.setSize(160, 36);
button.setOnAction(() -> System.out.println("Clicked"));
AvrixUi.add(button);
```

Widgets can be removed with `AvrixUi.remove(button)`. A default system font is loaded
automatically; replace it with `AvrixUi.setDefaultFont(font)`.

---

### 🧵 Threading

LixUI objects are single-threaded. Use the convenience methods or `AvrixUi.run(...)` from the game
thread:

```java
AvrixUi.run(() -> AvrixUi.manager().add(new Label("Status")));
```

`AvrixUi.manager()` returns the live `WidgetManager` and must be called on the render thread.
`AvrixUi.isReady()` reports whether the overlay has been initialized.

---

### 🎛️ Widgets and Themes

All LixUI types are bundled with the API, including layouts, inputs, tables, dialogs and charts:

```java
VBox box = new VBox();
box.add(new Label("Settings"));
box.add(new Button("Apply"));
AvrixUi.add(box);
```

Use `AvrixUi.setTheme(theme)` to replace the theme for every attached widget. See the
[LixUI documentation](https://github.com/Brov3r/LixUI) for the full widget catalogue.

---

### 🎨 Raw Drawing, Fonts and Animations

`RawDrawWidget` exposes the full LixUI rendering surface: primitives, text, images, masks,
clipping and transforms. The callback runs on the render thread in viewport coordinates.

```java
RawDrawWidget canvas = new RawDrawWidget((renderer, frame) -> {
    renderer.fillRect(new Rect(24, 24, 240, 96), Paint.solid(Color.ofHex("#FF8000")));
    renderer.withTransform(Transform.rotation((float) frame.elapsedSeconds()), () -> {
        renderer.strokeCircle(new Point(160, 160), 48, 2, Paint.solid(Color.WHITE));
    });
    renderer.text(AvrixUi.context().defaultFont(), "LixUI", 32, 140, Color.WHITE);
});
AvrixUi.add(canvas);
```

Fonts are created on the render thread and can be assigned immediately:

```java
AvrixUi.createFont(fontData, 18f, font -> label.setFont(font));
```

Animations are driven by the shared animator, which advances once per overlay frame:

```java
AvrixUi.animator().animate(0f, 1f, 0.4f, Easing.EASE_OUT,
        value -> label.setTextColor(Color.WHITE.withAlpha(value)));
```

The same services are available on the render thread through `AvrixUi.manager()`,
`AvrixUi.context()`, `AvrixUi.renderer()` and `AvrixUi.animator()`.

---

### 🖱️ Input Capture

While the pointer is over a visible widget, Avrix UI blocks game mouse buttons and camera zoom.
While a widget owns keyboard focus, game key states and Lua key events are suppressed, so text
fields can be used without moving the character.
