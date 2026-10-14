# Icon Placement

Place application icon files here.

## Windows installer and shortcut icon

Put your `.ico` file at:

```text
assets/icons/windows/SimpleGenomeHub.ico
```

Once this file exists:

- the Windows installer `exe` will use it
- Start Menu and desktop shortcuts created by the installer will use it
- the application window will also try to load it on Windows

## macOS app icon

Put your `.icns` file at:

```text
assets/icons/macos/SimpleGenomeHub.icns
```

Once this file exists:

- the generated `SimpleGenomeHub.app` will use it
- the macOS `pkg` will install an app bundle with that icon

## Optional window icon fallback

If you also want a general image fallback for the Java window icon, you can add:

```text
assets/icons/app.png
```

When both files exist, the application window prefers `app.png`.
