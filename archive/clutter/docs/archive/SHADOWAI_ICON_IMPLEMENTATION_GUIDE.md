# ShadowAI Icon Implementation Guide

## Quick Start

### Step 1: Generate Icons
Use the design specification in [`SHADOWAI_ICON_DESIGN_SPEC.md`](SHADOWAI_ICON_DESIGN_SPEC.md) with an AI image generator (Midjourney, DALL-E, Stable Diffusion) or provide it to a designer.

**Required Outputs:**
1. `ic_launcher_foreground.png` - Hooded figure with glowing eyes (264×264 px)
2. `ic_launcher_background.png` - Dark emerald to black radial gradient (108×108 dp)
3. `ic_launcher_monochrome.png` - White-only version for Android 13+ theming
4. `ic_notification.png` - 24dp flat white notification icon

### Step 2: Place Icons in Project
Copy the generated PNG files to these locations:

```
app/src/main/res/mipmap-anydpi-v26/
├── ic_launcher_foreground.png    ← Your generated foreground
├── ic_launcher_background.png    ← Your generated background
└── ic_launcher_monochrome.png    ← Your generated monochrome version

app/src/main/res/drawable/
└── ic_notification.png           ← Your generated notification icon
```

### Step 3: Generate Legacy Icons
For older Android versions, generate multiple density versions:

```bash
# Using Android Studio's Image Asset Studio
# Right-click app/src/main/res → New → Image Asset
# Or use command-line tools to generate all densities
```

Required legacy densities:
- `mipmap-hdpi/ic_launcher.png` (72×72 px)
- `mipmap-mdpi/ic_launcher.png` (48×48 px)
- `mipmap-xhdpi/ic_launcher.png` (96×96 px)
- `mipmap-xxhdpi/ic_launcher.png` (144×144 px)
- `mipmap-xxxhdpi/ic_launcher.png` (192×192 px)

### Step 4: Verify Configuration
The adaptive icon XML is currently configured in [`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`](app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml):

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
```

**CRITICAL:** After you generate and place the `ic_launcher_monochrome.png` file, add the monochrome layer:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
```

Do NOT add the monochrome line until the actual PNG file exists, or the build will fail.

### Step 5: Build and Test
```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Install on device
./gradlew installDebug
```

---

## Quality Validation

### Before Integration
Run these sanity checks on each generated icon:

#### Foreground (ic_launcher_foreground.png)
```bash
# Test visibility on white background
# Open in image editor, place on white canvas
# If not instantly visible → REGENERATE
```

**Checklist:**
- [ ] Fits completely inside 264×264 px safe zone
- [ ] No outer glow touching edges
- [ ] Visible on pure white background
- [ ] Armor is #121212 → #1E1E1E (NOT #000000)
- [ ] Eyes are #00E676
- [ ] Edge highlights are #2A2A2A
- [ ] Mid-tone contrast is clear
- [ ] Brighter than background
- [ ] Centered and symmetrical
- [ ] Occupies ≤60% of canvas

#### Background (ic_launcher_background.png)
**Checklist:**
- [ ] Radial gradient from #03110A to #000000
- [ ] No symbols, texture, noise, or glow
- [ ] Darker than foreground
- [ ] Smooth gradient (no banding)

#### Monochrome (ic_launcher_monochrome.png)
**Checklist:**
- [ ] White-only (#FFFFFF)
- [ ] No gradients or shadows
- [ ] Simplified foreground representation
- [ ] Transparent background

#### Notification Icon (ic_notification.png)
**Checklist:**
- [ ] 24×24 dp dimensions
- [ ] Flat white (#FFFFFF)
- [ ] No gradients or shadows
- [ ] Minimalist design
- [ ] Transparent background

---

## Testing on Device

### 1. Adaptive Icon Preview
```bash
# Install app on Android 8.0+ device
# Check home screen launcher icon
# Verify adaptive icon behavior on different launcher shapes
```

### 2. Themed Icon Test (Android 13+)
```bash
# Enable themed icons in launcher settings
# Verify monochrome version is used
# Check that icon adapts to system theme colors
```

### 3. Notification Icon Test
```bash
# Trigger a notification from the app
# Verify notification icon appears correctly
# Check visibility on both light and dark notification shades
```

### 4. Legacy Icon Test
```bash
# Test on Android 7.1 and below
# Verify legacy icons display correctly
```

---

## Troubleshooting

### Issue: Icon not visible on white background
**Solution:** Regenerate foreground with higher contrast. Ensure armor is #121212 → #1E1E1E, not #000000.

### Issue: Outer glow touching edges
**Solution:** Reduce foreground size to ≤60% of canvas. Ensure no glow effects extend beyond 264×264 px safe zone.

### Issue: Icon looks muddy on OLED displays
**Solution:** Verify background is pure black (#000000) at edges. Ensure foreground has sufficient contrast.

### Issue: Themed icons not working on Android 13+
**Solution:** Ensure `ic_launcher_monochrome.png` is present and properly referenced in adaptive-icon XML.

### Issue: Notification icon not visible
**Solution:** Verify icon is pure white (#FFFFFF) on transparent background. Test on both light and dark notification shades.

---

## Alternative: Using Android Studio Image Asset Studio

If you prefer a GUI approach:

1. **Open Android Studio**
2. **Right-click** `app/src/main/res` → **New** → **Image Asset**
3. **Configure Foreground Layer:**
   - Source: Image file
   - Select your `ic_launcher_foreground.png`
   - Resize: Fit within safe zone
4. **Configure Background Layer:**
   - Source: Image file
   - Select your `ic_launcher_background.png`
5. **Configure Monochrome Layer:**
   - Source: Image file
   - Select your `ic_launcher_monochrome.png`
6. **Click Next** → **Finish**

Android Studio will automatically generate all required density versions.

---

## File Structure Reference

```
app/src/main/res/
├── mipmap-anydpi-v26/
│   ├── ic_launcher.xml                    ← Adaptive icon config (already updated)
│   ├── ic_launcher_foreground.png        ← Your generated foreground
│   ├── ic_launcher_background.png        ← Your generated background
│   └── ic_launcher_monochrome.png        ← Your generated monochrome
├── mipmap-hdpi/
│   └── ic_launcher.png                   ← Legacy icon (72×72 px)
├── mipmap-mdpi/
│   └── ic_launcher.png                   ← Legacy icon (48×48 px)
├── mipmap-xhdpi/
│   └── ic_launcher.png                   ← Legacy icon (96×96 px)
├── mipmap-xxhdpi/
│   └── ic_launcher.png                   ← Legacy icon (144×144 px)
├── mipmap-xxxhdpi/
│   └── ic_launcher.png                   ← Legacy icon (192×192 px)
└── drawable/
    └── ic_notification.xml               ← Notification icon (placeholder - replace with PNG)
```

---

## Next Steps

1. **Generate icons** using the specification in [`SHADOWAI_ICON_DESIGN_SPEC.md`](SHADOWAI_ICON_DESIGN_SPEC.md)
2. **Validate** each icon against the quality checklist
3. **Place** PNG files in the correct directories
4. **Generate** legacy density versions
5. **Build** and test on actual devices
6. **Iterate** if any issues arise

---

## Additional Resources

- [Android Adaptive Icons Guide](https://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive)
- [Notification Icon Design](https://developer.android.com/training/notify-user/notification)
- [Material Design Icon Guidelines](https://material.io/design/iconography/overview.html)

---

*Generated for ShadowAI Android Application*
*Implementation Guide Version 1.0*
