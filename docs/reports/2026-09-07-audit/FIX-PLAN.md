# Prioritert plan for fiksing

Grunnlag: gjennomgang av main/v1.9.3 og 65 nye tester. Produksjonskode er ennå ikke endret. Funn-ID-er viser til [rapporten](</Users/stian/Developer/Plugins/Jetbrains WebStorm/css-vars-assistant-audit-2026-09-07/docs/reports/2026-09-07-audit/REPORT.md>).

Målet er korrekte oppslag i CSS, SCSS, Sass og LESS, trygg oppførsel under redigering og lavere kostnad per completion. Behold eksisterende rangering og brukerfunksjoner mens de dokumenterte feilene rettes i avgrensede endringer.

## 1. Stans feil som avbryter eller overtar IDE-funksjoner

**F01, F04 og unntaket i F11.** Gjør dette som en liten, separat retting før større refaktorering.

- [ ] Innfør felles aktiv referansesti og arbeidsgrense gjennom CSS-/LESS-/SCSS-oppslag. Bevar rått, uavklart uttrykk ved syklus.
- [ ] Avgrens dokumentasjonsmål til faktiske deklarasjoner/referanser. Returner null for at-rules, kommentarer og andre irrelevante PSI-elementer.
- [ ] La uferdige eller ugyldige fargetall gi null/uavklart resultat uten NumberFormatException.
- [ ] Utvid kontrollene med CSS → LESS → CSS, flere ledd, gjentatte søskenreferanser og avbrutte operasjoner.

**Ferdig når:** syklustesten, provider-testene og den ugyldige fargetesten er grønne, de eksisterende Java-testene fra #35 fortsatt passerer, og ordinær CSS/SCSS/LESS-dokumentasjon fungerer.

## 2. Rett indeksens eierskap og cache-gyldighet

**F02, F03 og F12s serialisering.** Dette er den viktigste strukturelle forbedringen.

- [ ] Lag først en liten teknisk prøve som viser hvordan faktiske node_modules-/eksterne importfiler blir indeksérbare uten at DataIndexer leser andre filer. Kontroller JetBrains' API for ekstra indeksrøtter og prosjektets ekskluderte filer.
- [ ] Lagre deklarasjoner under faktisk kildefil. Bevar råverdi, offset/linje, kontekst og språkmetadata. Unngå å kopiere samme innhold inn i hver importør.
- [ ] Flytt importgrafen til et separat ansvar ved oppslag. Bevar eksisterende PROJECT_ONLY / PROJECT_WITH_IMPORTS / GLOBAL-funksjonalitet.
- [ ] Erstatt skilletegnsbasert pakking med et entydig format. Øk indeksversjonen ved format-/semantikkendring.
- [ ] Knytt cache til relevante indeks-, dokument-, import- og innstillingsendringer. Sørg for stabil scope-identitet og ryddig tjenestelivsløp.
- [ ] Test avhengighet som endres, slettes eller opprettes etter første oppslag, rename/move, endret package.json, usavede editorendringer, endret importdybde og to åpne prosjekter.

**Ferdig når:** red → blue oppdateres uten restart/re-index-knapp, source viser den reelle avhengighetsfilen, importgrafen følger filendringene og cachetestene passerer. En endring i avhengighetsfilen skal ikke kreve at alle importører manuelt reindekseres.

## 3. Samle trygg parsing og rett importvariantene

**F05, F06, F08 og deler av F07/F09.** Lag ikke enda en parallell regex-parser.

- [ ] Velg eksisterende språklexer der den er egnet for lett indeksering; ellers en liten skanner med eksplisitt status for strenger, escapes, kommentarer og balanserte grupper.
- [ ] Del denne skanningen mellom deklarasjonsuttrekk, lokale verdier og importuttrekk. Behold forskjeller i Sass-innrykk og CSS/SCSS/LESS-syntaks.
- [ ] Bevar deklarasjoner før og etter semikolon på samme linje, gyldige Unicode-navn, URL-er, strengverdier, blokkgrenser og kildeposisjoner.
- [ ] Bevar importenes rekkefølge, lister og alternativer. Rett LESS (reference), eksplisitte Sass-partialutvidelser og transitive overstyringer.
- [ ] Bruk en JSON-parser til package.json og egne små funksjoner for prioritering av entrypoints.
- [ ] Fjern regelen som forkaster preprosessorbaserte CSS-temaverdier når en direkte verdi allerede finnes.

**Ferdig når:** parser- og importtestene passerer, minifisert CSS og innrykket Sass beholder riktige kontekster, og ingen eksisterende #28/#29-regresjoner brytes.

## 4. Gjør verdioppslag bevisst på språk og brukssted

**F07 og F09.** Dette bør deles i små endringer per språkregel.

- [ ] Gi oppslaget aktiv fil, bruksposisjon, språk og relevant modul/scope. Undersøk IDE-ens eksisterende PSI-referanser før egen semantikk bygges.
- [ ] Implementer dokumentert støtte for Sass !default, global/lokal shadowing, deklarasjonsrekkefølge, bindestrek/understrek og namespacede @use-referanser.
- [ ] Skill LESS-scope og lazy evaluation fra Sass' deklarasjonsrekkefølge.
- [ ] Bruk ett felles oppslagsresultat for completion, hover og hint. Bevar originalt uttrykk, resolved verdi, kilde og oppløsningskjede.
- [ ] Skill mellom aktiv rekursjonssti og allerede behandlede søsken. Ikke substituer var()-tekst inne i strenger.
- [ ] Vis uavklart eller flere mulige verdier når statisk analyse mangler sikker kontekst. Unngå å utrope én global indeksverdi til faktisk runtime-vinner.

**Ferdig når:** first.$brand og second.$brand får hver sin verdi, bruk før en SCSS-tilordning ikke får den senere verdien, lokale LESS-verdier ikke lekker og gjentatte var()-referanser løses korrekt.

Videre testmatrise: @use as *, @forward hide/show/as, modulkonfigurasjon with, !global, null med !default, Sass/LESS-interpolasjon og like navn i ulike filer. Dette er videre dekning, ikke funksjonalitet som er verifisert i dagens audit.

## 5. Rett editorpresentasjon og farge-/størrelsesverdier

**F10, resten av F11 og visningsdelen av F12.**

- [ ] Bruk samme gyldige språkkontekst for completion og dokumentasjon; skill streng fra interpolasjon og deklarasjon fra flerlinjers verdi.
- [ ] Bevar bokstavbruk i alle råverdier og kildehenvisninger.
- [ ] Samle normalisering av hue/alpha/tall og støtt negative og ledende desimalstørrelser.
- [ ] Gjenbruk beregnede visningsdata og pikselkolonnelogikk i stedet for å beregne dette ulikt i flere steder.
- [ ] Gjennomgå relative px-estimater og kontrastvisning som en separat korrekthetsoppgave: viewport, font, bakgrunn og alpha påvirker resultatene. Dette er en kildebasert oppfølgingsoppgave; det er ikke bevist med nye kontrasttester i denne leveransen.

**Ferdig når:** gjeldende editor- og verditester passerer, valgt completion settes riktig inn med Enter/Tab, og dokumentasjonen visuelt viser riktige råverdier i lys og mørk IDE-modus.

## 6. Optimaliser målt arbeid og gjennomfør DRY/KISS-opprydding

**F13 og F14.** Ikke legg en ny cache over ukorrekte data.

- [ ] Parse den aktive filen én gang per completion; gjenbruk deklarasjonskartet for alle kandidater.
- [ ] Gjenbruk indekslesinger, dekodede verdier, fargeanalyse og oppløsningsresultater innen samme forespørsel.
- [ ] Flytt store navnelister til DEBUG og unngå strengbygging når logging er avslått.
- [ ] Legg inn avbruddssjekker i lange løkker og videresend ProcessCanceledException.
- [ ] Del completion-klassen i små ansvarsområder uten å endre den etablerte rangeringen.
- [ ] Fjern ubrukt ImportCache.add/replace hvis ingen funksjon trenger dem, og gjennomgå unødvendig global opprydding og eksplisitt GC.
- [ ] Kjør samme 100/1 000-tokenmåling før og etter, deretter 5 000/10 000 tokens i et representativt IDE-prosjekt. Registrer kald/varm kjøring, antall indekslesinger og tildelinger. Sett en realistisk terskel etter stabile målinger.

**Ferdig når:** den dokumenterte gjentatte filskanningen er borte, målingene viser forbedring og alle korrekthetstester fortsatt passerer. Påstanden om completion under 100 ms må enten dokumenteres med avgrenset testmiljø eller presiseres i produktteksten.

## 7. Samlet verifikasjon og kontrollert utgivelse

- [ ] Gjør alle 368 nåværende tester grønne og legg til de relevante tilleggstilfellene over. Behold forventninger som uttrykker korrekt språkoppførsel; ikke ignorer røde tester for å få grønt bygg.
- [ ] Kjør full suite, pluginstruktur og konfigurasjonskontroll.
- [ ] Kjør Plugin Verifier mot deklarert minimumsversjon og aktuelle støttede IDE-produkter/versjoner.
- [ ] Gjør manuell/sandbox GUI-kontroll av CSS, SCSS, Sass og LESS: hover, completion, innsetting, endring etter første oppslag, importkjeder, Java-doc fallback og indeksoppgradering.
- [ ] Skriv presise release notes og ha forrige plugin-ZIP som rollback. Publisering er et separat steg etter gjennomført fiksing og verifikasjon.

## Arbeidsform og mulig automatisering

Ta én sammenhengende rotårsak per PR. Flytt bare kode som faktisk deles; behold språkforskjeller tydelige. Gi hver PR en liten reproduksjon, nye/eksisterende tester og en konkret før/etter-beskrivelse.

En nyttig gjenbrukbar skill kan være **jetbrains-language-regression-audit**: hent issue-eksempler, plasser dem i en språkmatrise, kjør baseline og editor-fixtures, og produser samme testbevis som her. Bruk de eksisterende issue-rapportene som inngang fremfor å lage en ny, duplisert issue-monitor. CI kan kjøre regresjoner ved PR og en periodisk IDE-kompatibilitetsmatrise. Ingen skill eller automation er opprettet nå.

