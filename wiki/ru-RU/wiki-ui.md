[Главная](../wiki-language.md) > [Документация](wiki-main.md) > UI API

# 🖥️ UI API

Пакет `com.avrix.api.ui` интегрирует [LixUI](https://github.com/Brov3r/LixUI) в клиент Project
Zomboid. Оверлей рисуется поверх готового кадра игры и перехватывает мышь и клавиатуру только
тогда, когда указатель находится над виджетом или виджет имеет фокус.

API работает только на клиенте. На выделенном сервере вызовы игнорируются.

---

### 🚀 Быстрый старт

Создайте виджет LixUI и зарегистрируйте его через `AvrixUi`. Метод можно вызывать из любого потока.

```java
Button button = new Button("Hello");
button.setPosition(24, 24);
button.setSize(160, 36);
button.setOnAction(() -> System.out.println("Clicked"));
AvrixUi.add(button);
```

Виджет удаляется через `AvrixUi.remove(button)`. Системный шрифт загружается автоматически;
заменить его можно через `AvrixUi.setDefaultFont(font)`.

---

### 🧵 Потоки

Объекты LixUI однопоточные. Используйте удобные методы или `AvrixUi.run(...)` из игрового потока:

```java
AvrixUi.run(() -> AvrixUi.manager().add(new Label("Status")));
```

`AvrixUi.manager()` возвращает активный `WidgetManager` и должен вызываться в render-потоке.
`AvrixUi.isReady()` сообщает, инициализирован ли оверлей.

---

### 🎛️ Виджеты и темы

Все типы LixUI поставляются вместе с API: layout-контейнеры, поля ввода, таблицы, диалоги и
графики:

```java
VBox box = new VBox();
box.add(new Label("Settings"));
box.add(new Button("Apply"));
AvrixUi.add(box);
```

Метод `AvrixUi.setTheme(theme)` заменяет тему для всех прикреплённых виджетов. Полный каталог
виджетов описан в [документации LixUI](https://github.com/Brov3r/LixUI).

---

### 🎨 Сырая отрисовка, шрифты и анимации

`RawDrawWidget` даёт полный доступ к рендерингу LixUI: примитивы, текст, изображения, маски,
клиппинг и трансформации. Callback выполняется в render-потоке в координатах viewport.

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

Шрифты создаются в render-потоке и сразу передаются в callback:

```java
AvrixUi.createFont(fontData, 18f, font -> label.setFont(font));
```

Анимации обновляются общим аниматором раз в кадр оверлея:

```java
AvrixUi.animator().animate(0f, 1f, 0.4f, Easing.EASE_OUT,
        value -> label.setTextColor(Color.WHITE.withAlpha(value)));
```

Те же сервисы доступны в render-потоке через `AvrixUi.manager()`, `AvrixUi.context()`,
`AvrixUi.renderer()` и `AvrixUi.animator()`.

---

### 🖱️ Перехват ввода

Пока указатель находится над видимым виджетом, Avrix UI блокирует кнопки мыши и зум камеры.
Пока виджет имеет фокус клавиатуры, состояния клавиш игры и Lua-события клавиш подавляются,
поэтому текстовые поля можно использовать без движения персонажа.
