- Every app icon is now a Material Symbols Rounded glyph; the selected
  bottom navigation tab shows its filled variant.
- `scripts/icons-fetch.py <id>|--all` writes the `shared` icons of
  `docs/design/icons.tsv` in one untinted format; each use site sets the
  tint, and `IconRegistryTest` enforces the format.
