# Reese's Sodium Options License (MIT)

The Reese's Sodium Options UI (`me.flashyreese.mods.reeses_sodium_options.*`,
`assets/reeses-sodium-options/*`) in Demonica is ported from
[FlashyReese/reeses-sodium-options](https://github.com/FlashyReese/reeses-sodium-options),
by way of Actinium's 1.12.2 port, and remains licensed under the MIT License:

> The MIT License (MIT)
>
> Copyright (c) 2021 FlashyReese
>
> Permission is hereby granted, free of charge, to any person obtaining a copy
> of this software and associated documentation files (the "Software"), to deal
> in the Software without restriction, including without limitation the rights
> to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
> copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in
> all copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
> FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
> THE SOFTWARE.

This notice comes from Actinium's `LICENSE-REESES-SODIUM-OPTIONS.md` at
`4a19c959`. Porting notes:

- Actinium's port removed the donation prompt, the Ko-fi support button and
  their config entries.
- The 1.12.2 port replaces the newer Minecraft GUI event model with the
  compatibility layer in `com.demonica.gui.rso.compat` (Actinium's
  `com.dhj.actinium.gui.rso.compat`). Actinium's port kept RSO's visual
  constants, layout math and widget behavior verbatim.
- RSO's rows read upstream Celeritas's option model
  (`org.taumc.celeritas.api.options.*`) through the `RsoOption` and
  `RsoModOptions` wrappers. `ExternalPage` and `ExternalButtonControl`, which
  Actinium's port added under the MIT License, now live in RSO's
  `client.gui.option` package.
