# Teknisk gjennomgang av CSS Variables Assistant

Dato: 7. september 2026. Undersøkt kode: **main / v1.9.3**, commit **1c9ea8e4478d69fd4e3121cc1b378f7d0fcf37f3**.

**Konklusjon:** Pluginen har nyttig funksjonalitet og en eksisterende testsuite som passerer, men testene dekker ikke flere viktige forskjeller mellom CSS, SCSS, Sass og LESS. Undersøkelsen bekrefter feil i rekursjon, indeksoppdatering, cache, språkforståelse og editorintegrasjon. Jeg anbefaler målrettede feilrettinger først, deretter en avgrenset omlegging av indeksering og verdioppslag.

**Verifisert resultat:** 303 eksisterende tester passerer. 65 nye tester er lagt til: **55 feiler og 10 passerer**. Totalt **368 tester: 313 bestått, 55 feilet, ingen hoppet over**. Flere tester demonstrerer samme rotårsak; dette er ikke 55 uavhengige bugs.

Produksjonskode og byggkonfigurasjon er uendret. Nye tester og rapporter ligger på **codex/audit-edge-cases-2026-09-07** i en separat worktree. Den opprinnelige mappen sto på **fix/issues-28-29-follow-up / 9c21162**, med lokale endringer i CHANGELOG.MD, docHelpers.kt og DocHelpersTest.kt. Disse og de eksisterende rapportene er bevart.

## Formål og arkitektur

Pluginen skal gjøre design tokens forståelige direkte i JetBrains-editoren: completion for CSS custom properties og preprosessorvariabler, dokumentasjon med temaer, verdier, farger og kilder, og oppslag gjennom importer og aliaser. Støttede språkflater i plugin.xml er CSS, SCSS, Sass og LESS. Produktbeskrivelsen retter seg mot IntelliJ IDEA Ultimate, WebStorm, PhpStorm, PyCharm Professional, GoLand og RubyMine.

De sentrale dataflytene er:

1. CSS- og preprosessorindekser leser deklarasjoner.
2. ImportResolver finner relative filer, pakker og Sass-moduler.
3. ScopeUtil og cacher velger filer og husker resultater.
4. Completion og dokumentasjon leser indeksen, løser aliaser og formaterer visningen.

Hovedproblemet er at indeks, regex-parsere og prosjektglobale oppslag sammen brukes som en forenklet språkfortolker. Informasjon om faktisk kildefil, lokalt scope, modulnavn og bruksposisjon går tapt. Dermed kan en forbedring for ett språk gjøre et annet språk mindre korrekt.

## Nylige GitHub-issues

Live kontroll viste **én åpen issue og ingen nyere issue enn #35**. Et øyeblikksbilde av alle 11 issues er lagret i [issues.json](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/docs/reports/2026-09-07-audit/issues.json>).

| Issue | Status ved kontroll | Betydning for undersøkelsen |
| --- | --- | --- |
| [#35 – Plugin breaks quick documentation](https://github.com/Stianlars1/css-vars-assistant/issues/35) | Åpen; opprettet 24. august, sist oppdatert 28. august | Fiksen og Java-regresjonstestene finnes på aktuell main. Den rapporterte Java-feilen regnes derfor ikke som en ny, ufikset feil. Språkavgrensningen har likevel gjenværende hull innenfor stylesheet-PSI, se F04. |
| [#28 – Sass @use / @forward](https://github.com/Stianlars1/css-vars-assistant/issues/28) | Lukket | Traversering er innført. Full modulsemantikk, importvarianter og rekkefølge er fortsatt mangelfulle. |
| [#29 – root/theme normalization](https://github.com/Stianlars1/css-vars-assistant/issues/29) | Lukket | Det finnes regresjonsdekning, men minifisert CSS og innrykket Sass har egne kontekstfeil. |
| [#26 – SCSS alias to CSS custom property](https://github.com/Stianlars1/css-vars-assistant/issues/26) | Lukket | Aliasstøtten virker i eksisterende eksempler, men blandede LESS/CSS-sykluser og scoping er ikke tilstrekkelig håndtert. |

Ingen issues er endret eller kommentert. Marketplace-godkjenning og installert versjon hos brukerne er ikke kontrollert i denne gjennomgangen.

## Prioriterte funn

P1 = bør tas først fordi feilen kan avbryte funksjonalitet, overta IDE-funksjoner eller vise utdaterte data. P2 = konkret korrekthets- eller ytelsesproblem. P3 = latent feil i en funksjon uten funnet produksjonskall.

### F01 · P1 · Blandet LESS/CSS-syklus gir StackOverflowError

Reproduksjon:

```less
@alias: var(--loop);
:root { --loop: @alias; }
```

Oppslag av `var(--loop)` ender i **StackOverflowError**. Overgangen fra CSS-resolveren til preprosessorresolveren og tilbake starter nye visited/depth-verdier. Den felles syklusen blir dermed aldri stoppet. Rene CSS- og LESS-sykluser terminerer i kontrolltestene.

Kilde: [docHelpers.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/documentation/docHelpers.kt:80>) og [retur til CSS-oppslag](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/documentation/docHelpers.kt:116>). Bevis: `testMixedLessAndCssCycleTerminates`.

**Tiltak:** Én resolverkontekst gjennom hele kjeden, med aktiv referansesti og total arbeidsgrense. Returner et uavklart resultat når koden er syklisk. Ikke behandle StackOverflowError som normal kontrollflyt.

### F02 · P1 · Importert innhold lagres under feil fil og blir utdatert

Begge indeksene leser eksterne importfiler og kopierer deklarasjonene inn i indeksverdien til importøren. Når bare en avhengighet endres fra `red` til `blue`, gir oppslaget fortsatt **red**. Hover oppgir dessuten **app.css:1** for en deklarasjon som ligger i **tokens.css:1**.

Kilde: [CssVariableIndex.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/index/CssVariableIndex.kt:88>), [PreprocessorVariableIndex.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/index/PreprocessorVariableIndex.kt:59>) og [CssVariableDocumentationService.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/documentation/CssVariableDocumentationService.kt:74>). Bevis: `testImportedCssValueRefreshesWhenOnlyDependencyChanges`, `testImportedSourcePointsToDependencyFile`.

Dette strider direkte mot [JetBrains' indekskontrakt](https://plugins.jetbrains.com/docs/intellij/file-based-indexes.html#implementing-a-file-based-index): indeksdata må avhenge av filens eget input; avhengighet av andre filer gir foreldede data.

**Tiltak:** Indekser deklarasjoner under faktisk kildefil. Bruk en separat importgraf ved oppslag. Avklar støttet indeksering av eksterne filer og node_modules før den nåværende kopieringen fjernes.

### F03 · P1 · Cacher følger ikke vanlige filendringer

Tre kontroller feiler uten manuell cache-reset:

- LESS-verdi: indeksen har **blue**, mens resolvercachen fremdeles returnerer **red**.
- Importgraf: etter bytte fra first.css til second.css returnerer cachen fremdeles first.css.
- Nøkkelcache: en ny variabel finnes i indeksen, men mangler i samme gjenbrukte scope-cache.

Kilde: [PreprocessorUtil.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/util/PreprocessorUtil.kt:22>), [ImportCache.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/index/ImportCache.kt:22>), [CssVarKeyCache.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/completion/CssVarKeyCache.kt:37>). Bevis: `CacheLifecycleAuditTest`.

Nøkkelcachetesten verifiserer cache-API-et med samme scope. Den beviser ikke alene at alle completion-kall mister nye navn: ScopeUtil oppretter også nye scope-objekter, som kan omgå cachetreff. Dette er både en korrekthetsrisiko ved gjenbruk og et mulig hinder for effektiv caching.

**Tiltak:** Knytt gyldighet til relevante indeks-/dokumentendringer, importgraf og innstillinger. Bruk reelle scope-nøkler eller en stabil, innholdsbasert identitet, ikke bare hashCode. Ta med usavede editorendringer i den videre testmatrisen.

### F04 · P1 · Dokumentasjonsprovider overtar fortsatt feil stylesheet-elementer

Provider returnerer et variabelmål for **CSS @media**, **SCSS @use** og **@brand inne i en LESS-kommentar**. Det er samme type feilaktig målvalg som gjorde #35 alvorlig, men nå innenfor stylesheet-språkene. Testene bekrefter providerens feilaktige returverdi; en visuell kontroll av IDE-ens endelige popup gjenstår.

Kilde: [docHelpers.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/documentation/docHelpers.kt:135>) og [preprosessoruttrekk](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/documentation/docHelpers.kt:161>). Bevis: de tre første testene i `EditorBehaviorAuditTest`.

**Tiltak:** Gjenkjenn deklarasjoner og referanser fra PSI/token-type. Respekter språkets variabelprefiks, og avvis kommentarer, strenger og at-rules. Behold de eksisterende Java-testene fra #35.

### F05 · P2 · Regex-parsing ødelegger gyldige strenger og deklarasjoner

Bekreftede eksempler:

| Input | Nåværende resultat |
| --- | --- |
| `url("https://example.com/Logo.svg")` | Tolkes delvis som linjekommentar; CSS-tokenet forsvinner fra indeksen. |
| `"left;right"` i CSS eller SCSS | Verdi kuttes til `"left`. |
| `"/* literal */"` | Strenginnhold erstattes som om det var kommentar. |
| `--shadow:` over flere linjer, deretter `; --gap: 4px;` | Deklarasjonen --gap forsvinner. |
| Siste deklarasjon over flere linjer uten semikolon | Deklarasjonen forsvinner. |
| `--farge-blå` | Gyldig Unicode-navn blir ikke indeksert. |

Kilde: [CssTextUtil.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/util/CssTextUtil.kt:31>), [CssVariableEntryParser.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/index/CssVariableEntryParser.kt:28>) og [PreprocessorVariableIndex.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/index/PreprocessorVariableIndex.kt:87>). Bevis: `ParserEdgeCaseAuditTest`.

**Tiltak:** Bruk språkets lexer eller en liten tokenbevisst skanner med streng-, escape-, kommentar- og parentesstatus. Del trygg skanning mellom brukerne, men behold forskjeller mellom språk. [CSS-definisjonen](https://www.w3.org/TR/css-variables-1/#syntax) tillater langt mer enn dagens verdi-regex.

### F06 · P2 · Importvarianter og rekkefølge gir manglende eller feil verdier

Bekreftet:

- Importer i kommentarer og strenginnhold blir fulgt.
- `@import (reference) './tokens.less';` finner ingen fil.
- `@import 'first', 'second';` finner bare første Sass-fil.
- `@use './tokens.scss';` finner ikke den gyldige partial-filen _tokens.scss.
- Blandet @use/@import omordnes fordi to regex-søk samles etter hverandre.
- En LESS-temafil som importerer en base og deretter setter egen verdi, får baseverdien tilbake ved transitivt importoppslag.

Kilde: [ImportResolver.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/index/ImportResolver.kt:75>), [importuttrekk](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/index/ImportResolver.kt:118>) og [filoppslag](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/index/ImportResolver.kt:198>). Bevis: `ImportEdgeCaseAuditTest`.

**Tiltak:** Bevar syntaktisk rekkefølge, importtype og alternativer. Skill filoppdagelse fra evaluering av deklarasjoner. Støtten skal følge [Sass @use](https://sass-lang.com/documentation/at-rules/use/) og [LESS-importer](https://lesscss.org/features/#import-atrules-feature).

### F07 · P2 · SCSS/Sass/LESS behandles som et globalt navnekart

Bekreftede semantikkfeil:

- `$brand: red; $brand: blue !default;` ender som **blue**, selv om eksisterende verdi skal beholdes.
- En lokal LESS-variabel overskriver prosjektoppslaget til globalvariabelen.
- En SCSS-bruk før en senere tilordning får den senere verdien.
- `$font_size` finnes ikke når den brukes som `$font-size`.
- To moduler med samme variabelnavn gir **blue for både first.$audit-brand og second.$audit-brand**, selv om first skal gi red.
- `!default` inne i en sitert tekst blir fjernet.

Kilde: [PreprocessorVariableIndex.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/index/PreprocessorVariableIndex.kt:95>), [PreprocessorUtil.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/util/PreprocessorUtil.kt:108>) og [CssVariableCompletion.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/completion/CssVariableCompletion.kt:441>).

**Tiltak:** Ta med språk, kildefil, modul, scope og bruksposisjon ved oppslag. Bevar deklarasjonsflagg i indeksen. Undersøk først hvor mye IDE-ens egne referanser kan løse. Å forsøke å bygge en komplett Sass/LESS-kompilator inne i pluginen vil gjøre løsningen unødvendig stor. Reglene er dokumentert i [Sass Variables](https://sass-lang.com/documentation/variables/) og [LESS Variables](https://lesscss.org/features/#variables-feature).

### F08 · P2 · Temavarianter blandes eller forkastes

`:root{--bg:white}.dark{--bg:black}` indekseres som to **default**-verdier. Innrykket Sass mister også .dark-konteksten. I LESS blir `.dark { --bg: @night; }` forkastet dersom samme variabel allerede har en direkte verdi i :root.

Kilde: [CssVariableEntryParser.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/index/CssVariableEntryParser.kt:160>), [Sass-parsing](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/index/CssVariableEntryParser.kt:246>) og [CssVariableIndex.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/index/CssVariableIndex.kt:138>).

**Tiltak:** Oppdater kontekst ved faktiske blokk-/innrykksskifter, ikke én gang per tekstlinje. Bevar alle deklarasjoner; oppslag kan vurdere relevans senere. Indeksering skal ikke filtrere bort en annen temas gyldige aliasverdi.

### F09 · P2 · Variabeloppslag har flere ulike, motstridende regler

- `calc(var(--gap) + var(--gap))` blir **calc(4px + var(--gap))** fordi søskenreferanser behandles som syklus.
- To default-deklarasjoner på samme regel gir den første verdien i resolveVarValue, mens completion-hjelperen velger siste.
- Strengen `"var(--gap)"` blir endret til `"4px"`, selv om det er bokstavelig tekst.
- ResolutionInfo.original blir satt til sluttverdien etter rekursjon; dette påvirker kode som avgjør om avledet verdi skal markeres.
- Det separate lokale regex-oppslaget ignorerer siste deklarasjon uten semikolon.

Kilde: [docHelpers.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/documentation/docHelpers.kt:36>), [lokalt oppslag](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/documentation/docHelpers.kt:189>) og [CssVarCascadeUtil.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/completion/CssVarCascadeUtil.kt:7>).

**Tiltak:** Ett verdioppslag for completion, hover og hint. Bruk aktiv rekursjonssti per gren; bevar opprinnelig uttrykk. Ikke påstå å simulere full CSS-cascade uten DOM, selector-match og stilarkenes faktiske innlastingsrekkefølge.

### F10 · P2 · Completion aktiveres på feil steder og uteblir på gyldige steder

CSS-variabler tilbys inne i en kommentar med `var(...)`. SCSS-variabler tilbys inne i vanlig sitert tekst uten interpolasjon. En SCSS-verdi som fortsetter på neste linje blir derimot avvist av pluginens regel som antar at et variabelprefiks først på linjen er en deklarasjon.

Kilde: [CssVariableCompletion.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/completion/CssVariableCompletion.kt:83>) og [CssVariableCompletion.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/completion/CssVariableCompletion.kt:692>). Bevis: editor-fixturetester med reell completion og caret-posisjon.

**Tiltak:** Felles PSI-/tokenbasert kontekstkontroll for completion og dokumentasjon. Skill deklarasjon, bruk, streng og interpolasjon eksplisitt.

### F11 · P2 · Farge- og størrelsesparser gir feil eller kaster unntak

HSL/HSLA mister alpha. Hue utenfor 0–360 normaliseres feil. Store bokstaver i RGB-funksjon og desimalkanaler avvises. Midlertidig tekst som **1..2 50% 50%** kaster NumberFormatException i stedet for å gi et uavklart resultat. **-0.5rem** og **.5rem** klassifiseres ikke som størrelser.

Kilde: [ColorParser.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/documentation/ColorParser.kt:65>), [HSL-parsing](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/documentation/ColorParser.kt:132>) og [ValueUtil.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/util/ValueUtil.kt:9>). Bevis: `ValueEdgeCaseAuditTest`. Referanse: [CSS Color 4](https://www.w3.org/TR/css-color-4/).

**Tiltak:** Del normalisering av alpha, hue og tall. Parsing av uferdig editortekst må tåle ugyldig input. Behold originalverdien når evaluering ikke er mulig.

### F12 · P2 · Lagring og visning endrer brukerens innhold

Indeksseparatoren **|||** er gyldig inne i strenger og dokumentasjonskommentarer. Codec deler slike felt i flere poster. HTML-tabellen konverterer dessuten alle verdier til små bokstaver: **url(LogoDark.svg)** vises som **url(logodark.svg)**.

Kilde: [CssVariableIndexValueCodec.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/index/CssVariableIndexValueCodec.kt:27>) og [buildHtmlDocument.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/documentation/buildHtmlDocument.kt:182>).

**Tiltak:** Bruk lengdeprefikset/strukturert serialisering med rundturtester. Bevar råverdiens bokstavbruk i visningen. Oppdater indeksversjonen ved formatendring.

### F13 · P2 · Unødvendig arbeid i completion og importskanning

En diagnostisk fixturetest målte hele completeBasic-kallet etter at indeksdata var gjort tilgjengelige:

| Variabler i aktiv fil | Første kall | Gjentakelse 1 | Gjentakelse 2 |
| --- | ---: | ---: | ---: |
| 100 | 62,8 ms | 38,6 ms | 28,6 ms |
| 1 000 | 269,3 ms | 238,5 ms | 229,1 ms |

Målingene er fra sluttkjøringen, med IntelliJ IDEA Ultimate 2025.1-testplattform på denne maskinen. Prosjektet inneholder også 100-tokenfilen når 1 000-tokenfilen måles. IDE-oppslaget eksponerer 600 elementer i siste tilfelle; testene bruker derfor ikke totalt synlig antall som bevis på at pluginen mistet navn. Dette er en diagnostisk måling, ikke en generell ytelsesgaranti eller et etablert p95-benchmark.

Kildebevis for unødvendig arbeid:

- For hver matchet variabel skannes og kommentarstrippes den aktive filen på nytt via selectMainValue/lastLocalValueInFile: omtrent **O(k × filstørrelse)**.
- Preprosessor-completion leser samme nøkkel flere ganger fra indeksen.
- CSS/SCSS-importfiler leses gjentatte ganger under indeksering av importører.
- collectProjectImports går gjennom prosjektmappens VFS-tre og utelater bare node_modules eksplisitt.
- INFO-logging bygger hele navnelisten for hver completion.
- Flere lange løkker mangler eksplisitt avbruddssjekk; brede Exception-catcher må gjennomgås for korrekt videresending av ProcessCanceledException. Dette siste er kildebasert risiko, ikke en målt IDE-heng.

Kilde: [CssVariableCompletion.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/completion/CssVariableCompletion.kt:211>), [logging](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/completion/CssVariableCompletion.kt:407>) og [ImportResolver.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/index/ImportResolver.kt:45>).

**Tiltak:** Parse aktiv fil én gang per completion, memoiser oppslag innen forespørselen, gjenbruk dekodede verdier, flytt detaljer til DEBUG og bygg en gyldig cachemodell. Mål før/etter med samme fixture og deretter et faktisk IDE-prosjekt.

### F14 · P3 · ImportCache.add stopper etter første nye fil

`files.any { importedFiles.add(it) }` avbryter løkken straks én fil er lagt til. En batch med to nye filer beholder bare den første.

Kilde: [ImportCache.kt](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/src/main/kotlin/cssvarsassistant/index/ImportCache.kt:33>). Bekreftet av `testImportCacheAddsAllFilesInBatch`. **Ingen produksjonskall til add ble funnet**, så denne feilen forklarer ikke dagens aktive importflyt.

**Tiltak:** Fjern metoden hvis den fortsatt er ubrukt etter refaktorering, eller bruk en operasjon som legger til alle filer. Dette er et godt KISS-tiltak med lav prioritet.

## DRY/KISS: anbefalt struktur

Bevar skillet mellom filoppdagelse, parsing, oppslag og presentasjon. Det trengs ikke et generelt rammeverk eller en full preprosessorkompilator.

| Ansvar | Endring som gir konkret verdi |
| --- | --- |
| Tokenisering og deklarasjoner | Del sikker håndtering av strenger/kommentarer; ha tydelige språkregler. Fjern parallelle regex-varianter for lokale verdier. |
| Indeks | Lagre faktiske deklarasjoner, lokasjon og nødvendig språkmetadata én gang per kildefil. |
| Importgraf | Samle filoppslag, rekkefølge og importtype; bruk en JSON-parser for package.json fremfor mer regex og manuell objektklipping. |
| Resolver | Bruk én kontekst og én resultatmodell for completion, dokumentasjon og hint. |
| Completion | Del den 772 linjer lange klassen etter kontekstgjenkjenning, innsamling, rangering og presentasjon. Rangeringen har verdifull eksisterende testdekning som bør beholdes. |
| Dokumentasjon | Gjenbruk beregnede visningsdata, inkludert duplisert logikk for pikselkolonnen. Bevar råverdi og kildeinformasjon. |
| Livsløp | La prosjekt-/applikasjonstjenesten eie cachen. Gjennomgå duplisert global opprydding, indeksrebuild ved unload og eksplisitt System.gc(). |

## Testbevis og avgrensning

| Ny suite | Tester | Bestått | Feilet |
| --- | ---: | ---: | ---: |
| ParserEdgeCaseAuditTest | 18 | 2 | 16 |
| ValueEdgeCaseAuditTest | 10 | 1 | 9 |
| ResolutionEdgeCaseAuditTest | 10 | 3 | 7 |
| ImportEdgeCaseAuditTest | 9 | 2 | 7 |
| CacheLifecycleAuditTest | 5 | 0 | 5 |
| EditorBehaviorAuditTest | 12 | 1 | 11 |
| CompletionScalingAuditTest | 1 | 1 | 0 |
| **Sum** | **65** | **10** | **55** |

Alle testene er aktive. De feilede testene uttrykker forventet korrekt oppførsel og er grunnlaget for kommende fiksing; de er ikke ignorert eller omskrevet for å akseptere dagens bugs.

- Ren baseline: `./gradlew test --console=plain` → **303/303 bestått**.
- Full suite etter nye tester: samme kommando → **368 kjørt, 55 feilet**.
- `./gradlew verifyPluginProjectConfiguration verifyPluginStructure --console=plain` → **bestått**.
- Alle eksisterende Java-, import-, farge- og completion-regresjoner passerer også i sluttkjøringen.
- Produksjonskode er uendret. Ingen commit, push eller publisering er gjort.

Dette er målrettet dekning, ikke uttømmende bevis for alle edge-caser. Full GUI-verifikasjon, alle støttede IDE-versjoner, samtidighetsstress, remote IDE og full Sass/LESS-semantikk er ikke kjørt. Videre dekning er konkretisert i fikseplanen.

Bevis: [komplette testresultater](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/docs/reports/2026-09-07-audit/results.json>), [testlogg](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/docs/reports/2026-09-07-audit/full-suite.log>), [alle 65 nye tester](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/docs/reports/2026-09-07-audit/TEST-MATRIX.md>), [completion-målinger](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/docs/reports/2026-09-07-audit/completion-measurements.txt>).

## Neste steg

Følg [den prioriterte fikseplanen](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/docs/reports/2026-09-07-audit/FIX-PLAN.md>). Begynn med trygg terminering og dokumentasjonsavgrensning, og ta deretter indeks-/cachemodellen før større språk- og ytelsesrefaktorering.

