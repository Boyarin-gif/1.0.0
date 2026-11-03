# Plus Visual 5.2.2 — отчёт по обновлению

Основа: приложенный `Plus-Visual-5.2.0-cosmetics.zip`, Minecraft 1.21.11, Yarn `1.21.11+build.3`, Java 21.

## Реализовано в этой версии

### 1. Полноценный 3D-предпросмотр персонажа
- Экран кастомизации расширен: слева каталог, по центру трёхмерная модель игрока в полный рост, справа — настройки только для выбранного предмета, если они есть.
- Для отрисовки используется штатная GUI-система с `PlayerEntityModel` и `DrawContext.addPlayerSkin(...)`.
- Добавлены вращение зажатой левой кнопкой мыши и перетаскиванием, а также отдельные части косметики, прикреплённые к голове и корпусу preview-модели.
- Выбранная косметика показывается на модели ещё до экипировки; нажатие «Надеть»/«Снять» меняет сохранённое состояние.
- В предпросмотр включены ведьминская шляпа с исходным силуэтом, крылья, корона и катана.

### 2. Загрузка собственного PNG-скина
- Кнопка «Загрузить скин» открывает системный диалог выбора PNG.
- Принимается стандартный Minecraft skin PNG размером строго 64×64.
- Файл сохраняется в `config/plusvisual/preview_skin.png` и автоматически используется при следующих открытиях экрана.
- Скин применяется к 3D-предпросмотру внутри Plus Visual. Это локальный skin-preview; он не меняет профиль Mojang/Microsoft и не меняет скин, который видят другие игроки.

### 3. Подсветка настоящей брони друзей
- Удалено использование условного контура для режима «Часть брони».
- Добавлен Mixin к `EquipmentRenderer.render`: изменяется tint у штатной команды отрисовки настоящей модели надетой экипировки.
- Выбранная часть (шлем, нагрудник, поножи или ботинки) окрашивается выбранным цветом; форма и геометрия остаются ванильными.
- Изменение применяется только к игрокам из локального списка друзей. Режим обычного хитбокса сохранён отдельно.
- Новая логика не создаёт дополнительных геометрических оболочек.

### 4. Item Glow — палитра и цвет
- В настройках Item Glow добавлена палитра из существующих десяти цветов.
- Новая `ItemGlowRenderer` создаёт цветной вариант ванильной текстуры `minecraft:textures/misc/enchanted_glint_item.png` и устанавливает его через `TextureManager`.
- Существующая логика выбора главной и второй руки через `ItemStackMixin` сохранена; интенсивность продолжает регулироваться настройкой Power.
- При выключении Item Glow восстанавливается исходная текстура.
- Важно: Minecraft использует общую текстуру item-glint. Поэтому, пока Item Glow включён, выбранный цвет также получают другие предметы с обычным item-glint (например, в инвентаре или выброшенные предметы). Glint брони использует отдельный рендер; Team Glow не меняется.

### 5. Сохранённые оптимизации и косметика
- Сохранены оптимизация Trails, ограничения буфера/отрисовки и предыдущие правки симметрии крыльев, размера катаны, короны, хоткея V и friend-hitbox.
- Сохранены локальность косметики игрока и существующий каталог косметики.
- Версия поднята до `5.2.2`.

## Новые Java-классы
- `src/main/java/dev/pvpplus/client/PreviewSkinManager.java` — загрузка PNG, сохранение локального скина, композитная текстура и дополнительные части 3D-preview модели.
- `src/main/java/dev/pvpplus/client/ItemGlowRenderer.java` — динамическое перекрашивание item-glint текстуры с восстановлением исходной.
- `src/main/java/dev/pvpplus/mixin/ModelPartAccessor.java` — доступ к карте дочерних частей изолированной preview-модели.
- `src/main/java/dev/pvpplus/mixin/FriendArmorTintMixin.java` — изменение tint в реальном рендере экипировки друзей.

## Изменённые существующие файлы
- `src/main/java/dev/pvpplus/client/CosmeticsScreen.java`
- `src/main/java/dev/pvpplus/client/Features.java`
- `src/main/java/dev/pvpplus/client/FriendManager.java`
- `src/main/java/dev/pvpplus/client/PvpPlusClient.java`
- `src/main/java/dev/pvpplus/client/Automation.java`
- `src/main/java/dev/pvpplus/client/Effects.java`
- `src/main/java/dev/pvpplus/client/WorldOverlay.java`
- `src/main/java/dev/pvpplus/client/DragonWingsFeatureRenderer.java`
- `src/main/java/dev/pvpplus/client/KatanaFeatureRenderer.java`
- `src/main/java/dev/pvpplus/client/GoldCrownFeatureRenderer.java`
- `src/main/java/dev/pvpplus/mixin/TitleScreenMixin.java`
- `src/main/resources/pvpplus.mixins.json`
- `src/main/resources/fabric.mod.json`
- `gradle.properties`
- `README.md`, `VALIDATION.md`

## Новый ресурс
- `src/main/resources/assets/pvpplus/textures/cosmetic/preview_default_skin.png` — демонстрационный скин для предпросмотра, если пользователь ещё не загрузил свой.

## Проверки и ограничения
- JSON `fabric.mod.json` и `pvpplus.mixins.json` проверены на корректность.
- Сигнатуры основных API сверены с Yarn `1.21.11+build.3`: `PlayerEntityModel.getTexturedModelData`, `EquipmentRenderer.render`, `RenderCommandQueue.submitModel`, `NativeImage`, `TextureManager` и GUI-рендер игрока.
- ZIP можно проверить на целостность; исходный код и ресурсы включены.
- **Gradle-сборка не выполнена:** в контейнере нет команды `gradle`, в исходном проекте отсутствует Gradle Wrapper, а сетевое разрешение имён для загрузки дистрибутива Gradle недоступно. Поэтому я не утверждаю, что проект успешно скомпилирован.
- Minecraft 1.21.11 не запускался. Не проверены непосредственно в игре: реальное положение preview-косметики, открытие системного диалога на разных ОС, визуальный tint брони на живом игроке и цвет item-glint в обеих руках.
- Новые mixin-инъекции имеют `require = 0`, чтобы отсутствие целевого вызова не ломало запуск клиента; до теста на 1.21.11 нельзя утверждать, что все injection points активировались именно в установленной сборке.

## Сборка

Требуется JDK 21 и Gradle 9.x. В корне проекта:

```bash
gradle build --no-daemon
```

После успешной сборки мод JAR будет в `build/libs/`.
