# ShadowAI Android Adaptive Icon Design Specification

## Overview
Premium AI chat application requiring stealth-focused, high-contrast adaptive icon system for Android.

---

## CRITICAL TECHNICAL REQUIREMENTS

### Foreground Layer (ic_launcher_foreground.png)
**Dimensions:** 264×264 px (adaptive icon safe zone)
**Format:** PNG with transparent background
**Canvas Size:** 108×108 dp (Android adaptive icon standard)

#### Visual Specifications
- **Subject:** Hooded assassin/rogue figure
- **Composition:** Centered and symmetrical
- **Occupancy:** Maximum 60% of canvas (no more than 158.4×158.4 px)
- **Style:** Stealth, premium, modern, professional
- **Lighting:** Flat lighting, sharp silhouette
- **No:** Text, logos, cartoon style, painterly style

#### Color Palette (CRITICAL - DO NOT DEVIATE)
```
Armor (primary): #121212 → #1E1E1E (matte dark charcoal)
  - ABSOLUTELY NOT #000000 (pure black)
  - Must have mid-tone contrast
  - Must be brighter than background

Eyes (accent): #00E676 (emerald green glow)

Edge highlights: #2A2A2A (subtle)
```

#### Technical Constraints
- **NO outer glow touching edges** - critical for adaptive icon masking
- **Must sit entirely inside 264×264 px safe zone**
- **Clear mid-tone contrast** - must be visible on white background
- **Brighter than background** - foreground must pop against dark background

#### Sanity Test
Before finalizing:
1. Drop foreground PNG onto pure white canvas
2. If you can't see it instantly → Android won't either
3. Must pass this test to be acceptable

---

### Background Layer (ic_launcher_background.png)
**Dimensions:** 108×108 dp (Android adaptive icon standard)
**Format:** PNG

#### Visual Specifications
- **Type:** Minimal radial gradient
- **No symbols, no texture, no noise, no glow**
- **Style:** Flat or very soft gradient

#### Color Palette
```
Center: #03110A (very dark emerald)
Edges: #000000 (pure black)
```

#### Gradient Specification
- Radial gradient from center outward
- Smooth transition, no banding
- Darker than foreground (critical for contrast)

---

## ADDITIONAL REQUIRED OUTPUTS

### 1. Monochrome Version (ic_launcher_monochrome.png)
**Purpose:** Android 13+ themed icons
**Format:** White-only, flat
**Style:** Simplified version of foreground
**Color:** Pure white (#FFFFFF) on transparent background
**No:** Gradients, shadows, or multiple colors

### 2. Notification Icon (ic_notification.png)
**Dimensions:** 24×24 dp
**Format:** PNG with transparent background
**Style:** Flat white, no gradients or shadows
**Color:** Pure white (#FFFFFF)
**Simplified:** Minimalist representation of hooded figure

---

## IMAGE GENERATION PROMPT

Use this exact prompt with your preferred AI image generator:

```
Create an Android adaptive app icon for a premium AI chat application named ShadowAI.

Foreground (critical):
A hooded assassin / rogue figure, centered and symmetrical, occupying no more than 60% of the canvas.
Matte dark charcoal armor (not pure black) with subtle edge highlights.
Clearly defined silhouette with strong mid-tone contrast.
Eyes glowing emerald green (#00E676).
NO outer glow touching the edges.
Transparent background.
Designed to fit completely inside a 264×264 px adaptive icon safe zone.

Background:
A minimal radial gradient from very dark emerald (#03110A) at the center to pure black (#000000) at the edges.
No symbols, no texture, no noise, no glow.

Style:
Stealth, premium, modern, professional, high-contrast, OLED-friendly.
Flat lighting. Sharp silhouette.
No text. No logos. No cartoon or painterly style.

Additional outputs:
– A white-only monochrome version for Android 13 themed icons
– A flat white notification icon (24dp) with no gradients or shadows
```

---

## ANDROID IMPLEMENTATION NOTES

### Required Files Structure
```
app/src/main/res/
├── mipmap-anydpi-v26/
│   ├── ic_launcher.xml
│   ├── ic_launcher_foreground.png (264×264 px)
│   ├── ic_launcher_background.png (108×108 dp)
│   └── ic_launcher_monochrome.png (for Android 13+)
├── drawable/
│   └── ic_notification.png (24×24 dp)
└── mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/
    └── ic_launcher.png (legacy icons)
```

### Adaptive Icon XML Configuration
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
```

### AndroidManifest.xml Reference
```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    ...>
```

---

## QUALITY CHECKLIST

### Foreground Validation
- [ ] Fits completely inside 264×264 px safe zone
- [ ] No outer glow touching edges
- [ ] Visible on pure white background (sanity test passed)
- [ ] Armor is #121212 → #1E1E1E (NOT #000000)
- [ ] Eyes are #00E676
- [ ] Edge highlights are #2A2A2A
- [ ] Mid-tone contrast is clear
- [ ] Brighter than background
- [ ] Centered and symmetrical
- [ ] Occupies ≤60% of canvas

### Background Validation
- [ ] Radial gradient from #03110A to #000000
- [ ] No symbols, texture, noise, or glow
- [ ] Darker than foreground
- [ ] Smooth gradient (no banding)

### Monochrome Validation
- [ ] White-only (#FFFFFF)
- [ ] No gradients or shadows
- [ ] Simplified foreground representation
- [ ] Transparent background

### Notification Icon Validation
- [ ] 24×24 dp dimensions
- [ ] Flat white (#FFFFFF)
- [ ] No gradients or shadows
- [ ] Minimalist design
- [ ] Transparent background

---

## OLED-FRIENDLY DESIGN PRINCIPLES

1. **Pure Black Background:** #000000 saves power on OLED displays
2. **High Contrast:** Foreground must be clearly visible
3. **No Unnecessary Glows:** Reduces power consumption
4. **Flat Design:** Minimizes rendering overhead
5. **Sharp Silhouette:** Ensures clarity at small sizes

---

## COMMON PITFALLS TO AVOID

❌ **DO NOT:**
- Use pure black (#000000) for foreground armor
- Add outer glow that touches icon edges
- Make foreground too large (>60% of canvas)
- Use cartoon or painterly styles
- Add text or logos to the icon
- Create foreground that's invisible on white background
- Use gradients in monochrome version
- Add shadows to notification icon

✅ **DO:**
- Test foreground on white background
- Ensure high contrast between layers
- Keep design simple and recognizable
- Use exact color codes specified
- Maintain symmetrical composition
- Create sharp, clean silhouettes

---

## DELIVERABLES SUMMARY

1. **ic_launcher_foreground.png** - Hooded figure with glowing eyes
2. **ic_launcher_background.png** - Dark emerald to black radial gradient
3. **ic_launcher_monochrome.png** - White-only version for theming
4. **ic_notification.png** - 24dp flat white notification icon

All files must pass the quality checklist above before integration.

---

*Generated for ShadowAI Android Application*
*Design System Version 1.0*
