# Play Store Graphics

Generated assets for Google Play Console. Regenerate from the Android project root:

```bash
bash ~/.cursor/skills/generate-app-assets/scripts/generate-app-icon.sh .
./gradlew generatePlayStoreAssets
bash ~/.cursor/skills/generate-app-assets/scripts/verify-play-store-assets.sh .
```

| Asset | Size | Path |
|-------|------|------|
| App icon | 512×512 | `app-icon-512.png` |
| Feature graphic | 1024×500 | `feature-graphic.png` |
| Phone screenshots | 1080×1920 | `phone/` |
| Tablet screenshots | 1600×2560 | `tablet/` |

Listing copy: `listing-descriptions.md`.
