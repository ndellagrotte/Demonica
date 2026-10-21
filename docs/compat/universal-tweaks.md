# Universal Tweaks: the shader pack list could not be scrolled

## Symptom

With Universal Tweaks installed ([#41](https://github.com/ndellagrotte/Demonica/issues/41)), the Shader Packs
screen's list did not scroll: the mouse wheel did nothing, and dragging the scrollbar moved the list until the
button was released, when it snapped back to the top. The shader options list, built the same way, had the
same problem. Without Universal Tweaks both scrolled.

## Cause

Universal Tweaks' smooth scrolling (`UTGuiSlotMixin`, in `mixins.misc.smoothscrolling.json`, on by default)
adds a `float target` to vanilla `GuiSlot`. At the head of every `GuiSlot.drawScreen`, while mouse button 0
is up, it overwrites `amountScrolled` by easing it towards `target`; with no animation started the easing is
instant. It moves `target` only inside `GuiSlot.handleMouseInput()`: from the wheel, and from `amountScrolled`
while the button is held (the vanilla scrollbar drag).

Iris's lists (`IrisGuiSlot`, the base of `ShaderPackSelectionList` and `ShaderPackOptionList`) extend
`GuiSlot` but never call `GuiSlot.handleMouseInput()`. `ShaderPackScreen.handleMouseInput` reads the wheel
itself and calls `IrisGuiSlot.mouseScrolled`, which writes `amountScrolled` directly, and the scrollbar drag
runs in `IrisGuiSlot.drawScreen` from its own drag origin. So `target` stayed at 0, and every
`super.drawScreen` pulled `amountScrolled` back to it: a wheel step was undone in the same frame, and a drag
held only while the button was down.

## Fix

`SmoothScrollingCompat.syncTarget` writes Iris's position into the mixin's `target` field (found once by
reflection on `GuiSlot`; vanilla has no field of that name, so a `float target` can only come from such a
mixin) just before `IrisGuiSlot.drawScreen` calls `super.drawScreen`. With `target` equal to
`amountScrolled`, the easing returns the position unchanged, and the mixin's clamps behave like vanilla's.
Without the field the class does nothing. The wheel stays a plain step on Iris's lists, as before.

## Repro

```
./gradlew runClient -PwithUniversalTweaks -PdevScript=@scripts/shaderpacks-scroll.txt -Dorg.gradle.jvmargs=-Xmx2G
```

`-PwithUniversalTweaks` loads Universal Tweaks 1.21.0 (its MixinBooter and ConfigAnytime dependencies are
built into Cleanroom). The script puts twelve dummy packs in `shaderpacks` so the list overflows, opens the
Shader Packs tab, turns the wheel and drags the scrollbar through the harness's `mouse`, `wheel`, `mousedown`
and `mouseup` steps, and checks the scroll position with `expect-list-scroll`. Before the fix the first check
failed at 0 pixels; the same script also passes without the flag. The harness feeds the events through a proxy
of lwjgly's window bridge (`ScriptedMouse`), so `Mouse.isButtonDown` reports the scripted drag as held, as it does
for a real one.
