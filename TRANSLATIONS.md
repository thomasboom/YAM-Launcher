# Contributing Translations

Thank you for your interest in translating YAM Launcher! This guide will help you add a new language translation to the app.

## Quick Start

### 1. Create Language Folder

Create a new folder in `/app/src/main/res/` named `values-[language-code]` (e.g., `values-pl` for Polish, `values-ja` for Japanese, `values-ar` for Arabic).

### 2. Copy English Files

Copy the English files from `values/` to your new folder:

- **`strings.xml`** (required) - Contains all user-facing text
- **`arrays.xml`** (optional) - Contains UI option labels like "Tiny", "Small", "Left", "Center", etc.

### 3. Translate

Translate all string values while keeping the `name` attributes unchanged.

## File Structure

### Example strings.xml (English source)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!--Home Screen-->
    <string name="set_default_launcher_prompt">YAM Launcher is not set as your default home app.</string>
    <string name="set_default_launcher_button">Set as Default</string>
    <string name="shortcut_default">Long press to add an app</string>
    
    <!-- Continue translating all strings... -->
    <string name="settings_title">Launcher Settings</string>
    <string name="search">Search…</string>
    <string name="confirm_yes">Yes</string>
    <string name="confirm_no">Cancel</string>
</resources>
```

### Example arrays.xml (English source)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="size_options">
        <item>Tiny</item>
        <item>Small</item>
        <item>Medium</item>
        <item>Large</item>
        <item>Extra Large</item>
        <item>Huge</item>
    </string-array>
    
    <string-array name="h_alignment_options">
        <item>Left</item>
        <item>Center</item>
        <item>Right</item>
    </string-array>
</resources>
```

## Translation Guidelines

### Do's

- **Keep `name` attributes in English** - Only translate the text content between tags
- **Preserve placeholders** - Keep `%1$s`, `%2$d`, etc. exactly as they are (used for dynamic values)
- **Keep special characters** - Preserve `…` (ellipsis), `\n` (line breaks), and other formatting
- **Translate naturally** - Use phrases that sound natural in your language, not word-for-word translations
- **Maintain context** - Consider where and how the text appears in the UI

### Don'ts

- **Don't translate accessibility strings** unless necessary (strings starting with `accessibility_`)
- **Don't translate string names** (the `name="..."` attribute)
- **Don't remove or add strings** - Keep the same structure as the English file
- **Don't translate `translatable="false"` strings** - Check the source file for these markers

## Submitting Your Translation

### Via Pull Request

1. **Fork** the repository on [Codeberg](https://codeberg.org/thomasboom/yamlauncher)
2. **Clone** your fork locally
3. **Create a new branch**: `git checkout -b add-[language]-translation`
   - Example: `git checkout -b add-polish-translation`
4. **Add your translation files** to the appropriate folder
5. **Commit** your changes: `git commit -m "Add [Language] translation"`
6. **Push** to your fork: `git push origin add-[language]-translation`
7. **Open a Pull Request** on Codeberg or GitHub

## Currently Supported Languages

- **English** (default)
- **Chinese** (中文)
- **Dutch** (Nederlands)
- **Finnish** (Suomi)
- **French** (Français)
- **German** (Deutsch)
- **Italian** (Italiano)
- **Japanese** (日本語)
- **Portuguese** (Português)
- **Brazilian Portuguese** (Português Brasileiro)
- **Russian** (Русский)
- **Spanish** (Español)
- **Ukrainian** (Українська)

## Questions?

Open an issue on [Codeberg](https://codeberg.org/thomasboom/yamlauncher/issues) and we'll help you get started.

Thank you for helping make YAM Launcher accessible to more people around the world!
