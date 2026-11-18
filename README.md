# Špajza – evidencia potravín doma (Android, Kotlin, Jetpack Compose)

Všetko, čo máte doma v chladničke, špajzi, pivnici a mrazničke: kde to je, čo sa kazí, čo treba kúpiť a čo z toho uvariť.
Plne offline, bez registrácie a cloudu. Zálohu robíte exportom súboru.

## Ako to otvoriť
1. Android Studio (Ladybug alebo novšie), `File > Open` → vyberte priečinok `Spajza`.
2. Počkajte na Gradle Sync (stiahne sa Gradle 8.9 a knižnice, treba internet).
3. Pripojte telefón (Android 12+) alebo spustite emulátor a stlačte Run.

## Funkcie
- Domov: prehľad, čo sa kazí, dochádzajúce zásoby, tip na recept, núdzové zásoby.
- Zásoby: vyhľadávanie, filtre (miesto, kategória), radenie, šarže s expiráciou, presuny medzi miestami.
- Skener EAN (kamera, ML Kit) + Open Food Facts, viacnásobné skenovanie, uloženie do vlastnej databázy (funguje potom offline), ručné zadanie kódu.
- Expirácia: farby, filtre, upozornenia 30/7/3 dni a po expirácii.
- Nákupný zoznam: návrhy z dochádzajúcich zásob, súčty, prenos označených do zásob.
- Recepty podľa zásob (21 vstavaných + vlastné), odpočet surovín po uvarení.
- Núdzové zásoby: na koľko dní vystačí jedlo a voda, čo chýba do cieľa, tipy a kontrolný zoznam.
- Štatistiky, história pohybov, miesta a kategórie, záloha/obnova (ZIP).

## Novšie funkcie
- Recepty s množstvami (kontrola, či máte dosť, voľba počtu porcií, presný odpočet po uvarení).
- Tlačidlo Späť po odobratí, zmazaní a prenose z nákupného zoznamu.
- Widget „Špajza – čo spotrebovať“ a denné upozornenie na dnes/zajtra.
- Hromadné pridanie po nákupe (skener, režim „Hromadne po nákupe“).
- Nákup z bločku (OCR) a OCR výživových hodnôt z obalu.
- Export núdzových zásob do PDF.

## Najnovšie funkcie
- Otvorené balenie: v detaile produktu (alebo tlačidlom „Otvoriť“ v zozname zásob) otvoríte balenie, skráti sa mu trvanlivosť
  (napr. mlieko 3 dni, syr 7 dní; odhad podľa druhu, dá sa zmeniť a zapamätať pre produkt) a môžete zadať, koľko z neho už zostalo.
  Otvorené balenia sú v zásobách označené a spotrebúvajú sa ako prvé. Otvorenie možno vrátiť tlačidlom Späť.
- Odhad výživových hodnôt: pre základné potraviny (cibuľa, zemiaky, mlieko, vajcia, múka…) sa dajú doplniť priemerné hodnoty
  na 100 g, keď ich nemožno nájsť podľa názvu ani EAN. Označia sa ako „odhad“ a dajú sa kedykoľvek prepísať údajmi z obalu.
- Navarené: tlačidlo „Navarené“ (Domov, Zásoby, Recepty) – vyberiete použité suroviny a množstvá a všetko sa odpočíta naraz
  jedným krokom (s možnosťou Späť). Navrhne najprv otvorené a čoskoro expirujúce potraviny a tie, čo používate často.
- Rýchle pridanie potraviny: namiesto dlhého formulára stačí názov, množstvo a dátum. Aplikácia navrhne miesto uloženia,
  jednotku, kategóriu, trvanlivosť (aj podľa vašich predchádzajúcich nákupov) a pri základných potravinách aj výživové hodnoty.
  Podrobný formulár zostáva dostupný.

## Ak aplikácia spadne
Pri ďalšom spustení zobrazí obrazovku s technickým popisom chyby a tlačidlom „Kopírovať chybu“.
