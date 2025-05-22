# CSS Variables Assistant - Plugin for JetBrains IDEs (IntelliJ, WebStorm, etc.)

Jobber du til tider, eller hele tiden, med `CSS`?
Da er dette pluginet for deg!

## Bakgrunn

Lenge har jeg irritert meg over at `IntelliJ`/`WebStorm` ikke har hatt støtte for å vise
**verdien** av en variabel __(typisk FFE, ol..)__ i popup menyen for `auto-completions`.

Det er ikke alltid like lett å huske hva `--ffe-color-accent-brand-emphasis-strongest` er, eller hva `--ffe-spacing-3xl`
er.

## Funksjonalitet

Pluginet er laget for å gi deg en bedre opplevelse når du jobber med `CSS`-variabler.
Pluginet gir deg:

- **Auto-completion** for `CSS`-variabler
- **Visning av verdier** i auto-completion
- **Visning av verdier** i `documentation` popup-menyen
- **Visning av context** i `documentation` popup-menyen

## Context
Context er en måte å vise verdier på som er spesifik for hvor du bruker variabelen og hva den representerer i en gitt
kontekst.

### Eksempler på context
- `dark` og `light` modus
- `prefers-color-scheme`
- `media queries`
- `max-width` og `min-width`
- `@media` queries
- `@supports` queries
- `@container` queries
- `@layer` queries
etc...

#### Eksempler på hvordan context kan brukes
```css
:root {
    /* Padding rundt viewport */
    --site-padding: 24px;
}

@media (max-width: 768px) {
    --site-padding: 20px;
    --dynamisk-farge: #FFF;
}

@media (max-width: 400px) {
    --site-padding: 16px;
}
```
I eksempelet over har vi en variabel som heter `--site-padding` som har forskjellige verdier basert på media-queries.
Når du bruker `--site-padding` i koden din, vil pluginet vise deg verdien av `--site-padding` avhengig av om den har en
tilhørende context eller ikke.

#### Auto-completion resultat-lista i de forskjellige variantene
##### Format
**Ved kontekst**
```
<IKON> - <VARIABEL_NAVN / ELLER KOMMENTAR OVER VARIABELNAVNET)>  <VERDI> / <VERDI> <VERDI>
```

**Uten kontekst**
```
<IKON> - <VARIABEL_NAVN / ELLER KOMMENTAR OVER VARIABELNAVNET)>  <VERDI>
```

##### Eksempel på Auto-completion resultat-rad
**Uten kontekst**

**Er av typen `ikke-farge` og har blitt redefinert 2 steder i media-queries**
```
<CSS_ikon> - Padding rundt viewport  24px (+ 2)
```

**Er av typen `ikke-farge` og er definert kun ett sted**
```
<CSS_ikon> - Padding rundt viewport  24px
```

**Er av typen `farge` og har blitt redefinert i en `@media (prefers-color-scheme-preference: dark)`**
```
<FARGE_PREVIEW_DEFAULT><FARGE_PREVIEW_DARKMODE> - dynamisk-farge  #FFF / 🌙 #000
```

**Er av typen `farge` og er definert kun ett sted**
```
<FARGE_PREVIEW_DEFAULT> - dynamisk-farge  #FFF
```

