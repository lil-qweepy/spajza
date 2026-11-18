package sk.spajza.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sk.spajza.app.data.Calc

private data class TipGroup(val title: String, val tips: List<String>)

private val tipGroups = listOf(
    TipGroup(
        "Voda",
        listOf(
            "Počítajte aspoň 2 litre pitnej vody na osobu a deň. Na varenie a základnú hygienu pridajte ďalšie 2–3 litre.",
            "Balenú vodu skladujte na tmavom a chladnom mieste, mimo chemikálií. Dátum spotreby na fľaši sledujte a zásobu obmieňajte.",
            "Domácu vodu skladujte len v čistých fľašiach určených na potraviny a obmieňajte ju každých 6 mesiacov.",
            "Pri výpadku vody si hneď naplňte vaňu a nádoby. Táto voda je vhodná na splachovanie a umývanie.",
            "Podozrivú vodu najprv sceďte cez čistú látku a potom ju aspoň 1 minútu povarte. Môžete použiť aj dezinfekčné tablety na vodu.",
            "Nikdy nepite vodu z povodní, žúmp alebo z neznámych zdrojov bez úpravy."
        )
    ),
    TipGroup(
        "Jedlo",
        listOf(
            "Vyberajte potraviny s dlhou trvanlivosťou, ktoré nepotrebujú chladenie ani dlhé varenie: konzervy, cestoviny, ryža, ovsené vločky, múka, sucháre, orechy, med, sušené ovocie, polievky v prášku.",
            "Cieľom je približne 2 000 kcal na dospelého denne. Údaj si upravte v Nastaveniach aplikácie.",
            "Pravidlo „prvé dnu, prvé von“: staršie potraviny dávajte dopredu a spotrebúvajte ich ako prvé, nové dávajte dozadu.",
            "Do zásob dajte aj otvárač konzerv, jedlo pre deti, domáce zvieratá a potraviny pre ľudí s diétou.",
            "Pri výpadku elektriny najprv spotrebujte potraviny z chladničky, potom z mrazničky a až nakoniec trvanlivé zásoby.",
            "Zásoby kontrolujte aspoň raz za 3 mesiace. Aplikácia vám v tom pomôže upozorneniami na expiráciu."
        )
    ),
    TipGroup(
        "Výpadok elektriny",
        listOf(
            "Majte pripravené baterky a náhradné batérie. Radšej LED svietidlá ako sviečky, ktoré sú rizikom požiaru.",
            "Nabité powerbanky, nabíjačky a rádio na batérie alebo kľučku. Po vypnutí siete bývajú vysielané pokyny cez rádio.",
            "Chladničku a mrazničku nechajte zatvorené. Plná mraznička udrží teplotu približne 1–2 dni, chladnička niekoľko hodín.",
            "Vytiahnite z elektriny citlivé spotrebiče, aby ich nepoškodil prepätie po obnovení dodávky. Nechajte svietiť jednu lampu, aby ste videli, že prúd sa vrátil.",
            "Telefón nechajte v úspornom režime a radšej píšte SMS, ako volajte. Šetrí to batériu aj záťaž siete.",
            "Generátory a plynové variče nikdy nepoužívajte vo vnútri ani v garáži. Hrozí otrava oxidom uhoľnatým."
        )
    ),
    TipGroup(
        "Teplo a vykurovanie",
        listOf(
            "Pri výpadku kúrenia sa sústreďte do jednej menšej miestnosti. Zatvorte dvere, utesnite škáry a zakryte okná dekami.",
            "Obliekajte sa do viacerých vrstiev a nezabudnite na čiapku, rukavice a ponožky. Spacie vrecia a deky majú byť poruke.",
            "Ak kúrite kachľami, krbom alebo plynom, vetrajte a majte v dome funkčný detektor oxidu uhoľnatého.",
            "Teplé nápoje a výdatné jedlo pomáhajú udržať telesnú teplotu. Alkohol nepomáha, naopak telo vychladzuje."
        )
    ),
    TipGroup(
        "Prvá pomoc a lieky",
        listOf(
            "Majte kompletnú lekárničku: obväzy, sterilné kompresy, náplasti, dezinfekciu, nožnice, rukavice, teplomer, kyselina acetylsalicylová alebo paracetamol, lieky na hnačku a na alergie.",
            "Pravidelne užívané lieky si skúste mať v zásobe na aspoň 14 dní. Termíny kontrolujte, o dávkovaní sa poraďte s lekárom alebo lekárnikom.",
            "Absolvujte kurz prvej pomoci. Vedieť poskytnúť resuscitáciu alebo zastaviť krvácanie môže zachrániť život.",
            "Tiesňové čísla: 112 (jednotné), 155 (záchranná služba), 150 (hasiči), 158 (polícia). Uložte si ich aj na papier."
        )
    ),
    TipGroup(
        "Hygiena a odpad",
        listOf(
            "Zásobte sa vlhčenými utierkami, toaletným papierom, mydlom, dezinfekciou rúk, hygienickými potrebami a odpadkovými vrecami.",
            "Pri nefunkčnej kanalizácii použite vedro s pevným vreckom a posypte ho napríklad pilinami alebo mačacím stelivom. Vrecká zaviažte a uložte mimo obytnej časti.",
            "Dôsledná hygiena rúk pred jedlom a po toalete znižuje riziko nákaz, keď nie je dostatok vody."
        )
    ),
    TipGroup(
        "Doklady, hotovosť a kontakty",
        listOf(
            "Majte pripravené kópie dokladov, poistiek a dôležitých zmlúv v obale, prípadne ich skeny na USB kľúči.",
            "Držte si hotovosť v menších bankovkách. Pri výpadku siete nemusia fungovať bankomaty ani platobné terminály.",
            "Dôležité telefónne čísla si zapíšte na papier. Dohodnite si s rodinou miesto stretnutia pre prípad, že sa nedovoláte.",
            "Nastavte si v telefóne kontakt „V prípade núdze“ (ICE) a zdravotné údaje dostupné aj pri zamknutej obrazovke."
        )
    ),
    TipGroup(
        "Pohotovostný batoh (72 hodín)",
        listOf(
            "Pripravte si batoh pre každého člena domácnosti, ktorý zvládnete odniesť: doklady, hotovosť, voda, trvanlivé jedlo, lieky, lekárnička, baterka, powerbanka, nabíjačky, teplé oblečenie a spacie vrece.",
            "Pridajte multifunkčný nôž, zapaľovač alebo zápalky, píšťalku, mapu, hygienické potreby a náhradné okuliare či zubnú protézu, ak ich používate.",
            "Batoh uložte pri vchode alebo na ľahko dostupné miesto a dvakrát ročne vymeňte potraviny a batérie."
        )
    ),
    TipGroup(
        "Ako sa riadiť v krízovej situácii",
        listOf(
            "Zachovajte pokoj a informujte sa z dôveryhodných zdrojov: rádio, oficiálne stránky a aplikácie záchranných zložiek, obec.",
            "Riaďte sa pokynmi hasičov, polície a civilnej ochrany. Ak vás vyzvú na evakuáciu, vezmite pohotovostný batoh a odíďte.",
            "Skontrolujte susedov, najmä starších, osamelých a ľudí s ťažkosťami. Vzájomná pomoc je pri kríze najspoľahlivejšia.",
            "Plánujte aspoň na 72 hodín, lepšie na 7 až 14 dní. Aplikácia vám v časti Núdzové zásoby ukáže, koľko dní zásoby vystačia."
        )
    ),
    TipGroup(
        "Situácia: siréna a ohrozenie",
        listOf(
            "✔ Ukryte sa v najbližšej pevnej budove, zatvorte okná a dvere, vypnite vetranie a klimatizáciu.",
            "✔ Zapnite rádio alebo televíziu a počúvajte pokyny. Informácie hľadajte len u oficiálnych zložiek a obce.",
            "✔ Vezmite so sebou pohotovostný batoh, lieky a doklady.",
            "✔ Informujte sa na obecnom či mestskom úrade, kde je najbližší úkryt a kde sú zverejnené pokyny pre vašu obec.",
            "✖ Nevychádzajte von zbytočne a nezahlcujte telefónne linky, tiesňové čísla používajte len pri skutočnej núdzi."
        )
    ),
    TipGroup(
        "Situácia: požiar v byte alebo dome",
        listOf(
            "✔ Zavolajte 112 alebo 150 a povedzte adresu, čo horí a či sú v budove ľudia.",
            "✔ Zachráňte seba a ostatných, zatvorte za sebou dvere a opustite budovu po schodisku.",
            "✔ Pri dyme sa pohybujte nízko pri zemi, kde je vzduch čistejší.",
            "✖ Nepoužívajte výťah a nevracajte sa po veci.",
            "✖ Horiaci olej nikdy nehaste vodou. Zakryte nádobu pokrievkou a vypnite zdroj tepla."
        )
    ),
    TipGroup(
        "Situácia: povodeň",
        listOf(
            "✔ Presuňte cennosti, doklady a elektroniku do vyšších poschodí a odpojte elektrinu, ak je to bezpečné.",
            "✔ Pri evakuácii vezmite batoh, lieky, doklady a domáce zvieratá.",
            "✖ Nevstupujte do zaplavených priestorov a neprechádzajte tečúcou vodou, aj plytká voda môže zraziť dospelého človeka.",
            "✖ Nepite vodu z vodovodu, kým obec neoznámi, že je opäť bezpečná."
        )
    ),
    TipGroup(
        "Situácia: extrémny mráz alebo horúčava",
        listOf(
            "✔ Mráz: obliekajte sa na cibuľu, pite teplé nápoje, kontrolujte starších susedov.",
            "✔ Horúčava: pite pravidelne vodu, zatienite okná, vyhýbajte sa slnku medzi 11. a 16. hodinou.",
            "✖ Dieťa ani zviera nikdy nenechávajte v zaparkovanom aute.",
            "✖ Nekúrte plynovým sporákom ani grilom v uzavretej miestnosti, hrozí otrava oxidom uhoľnatým."
        )
    ),
    TipGroup(
        "Oheň vonku a varenie bez elektriny",
        listOf(
            "Oheň a gril používajte len vonku, v bezpečnej vzdialenosti od budov, stromov a suchej trávy. Skontrolujte, či v danom období nie je v okolí zákaz rozkladania ohňa.",
            "Pripravte si vedro vody alebo hasiaci prístroj a oheň nikdy nenechávajte bez dozoru. Pred odchodom ho úplne uhaste a skontrolujte, či v ňom nie je žeravý popol.",
            "Kempingový varič na plyn alebo tuhé palivo používajte len vonku alebo pri dokorán otvorených dverách, nikdy v obytnej miestnosti ani v stane.",
            "Zapaľovač a zápalky držte mimo dosahu detí a uložte ich v suchu."
        )
    ),
    TipGroup(
        "Improvizované vykurovanie – radšej nie",
        listOf(
            "Domáce „hrnčekové ohrievače“ so sviečkami dodajú len minimum tepla a sú riziko požiaru. Nespoliehajte sa na ne.",
            "Bezpečnejšie je zmenšiť priestor: obývajte jednu miestnosť, utesnite škáry, zaveste na okná deky, spite v spacích vreciach a používajte fľaše s teplou vodou.",
            "Zdroj tepla na spaľovanie (kachle, krb, plyn) vyžaduje komín a vetranie. Mať v dome detektor oxidu uhoľnatého je dôležité.",
            "Ak potrebujete teplo, využite núdzové vykurovacie miesta, ktoré zriaďuje obec, a spýtajte sa na ne na obecnom úrade."
        )
    )
)

val checklist = listOf(
    "Pitná voda (2 l na osobu a deň)",
    "Trvanlivé potraviny a konzervy",
    "Otvárač konzerv",
    "Varič a palivo (používať len vonku)",
    "Baterky a náhradné batérie",
    "Powerbanka a nabíjačky",
    "Rádio na batérie alebo kľučku",
    "Lekárnička a osobné lieky",
    "Vlhčené utierky, toaletný papier, mydlo",
    "Odpadkové vrecia",
    "Zápalky alebo zapaľovač",
    "Spacie vrecia a deky",
    "Hotovosť v menších bankovkách",
    "Kópie dokladov a zoznam kontaktov",
    "Dezinfekčné tablety na vodu",
    "Multifunkčný nôž a píšťalka",
    "Detektor oxidu uhoľnatého",
    "Pohotovostný batoh"
)
@Composable
fun TipsScreen() {
    val nav = LocalNav.current
    val store = LocalStore.current
    val db = useDb()
    val s = db.settings
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    val waterNeed = s.persons * s.waterPerDayL * s.targetDays
    val kcalNeed = s.persons * s.kcalPerDay * s.targetDays
    val done = checklist.count { it in s.checked }

    ScreenScaffold(title = "Tipy a kontrolný zoznam", onBack = { nav.pop() }) { pad ->
        PageList(pad) {
            item {
                SCard {
                    SectionTitle("Čo potrebuje vaša domácnosť")
                    Muted("Pre " + s.persons + " " + daysText(s.persons.toDouble(), "osobu", "osoby", "osôb") + " na " + s.targetDays + " dní:")
                    Text("• Voda na pitie: " + Calc.num(waterNeed, 1) + " l")
                    Text("• Jedlo: " + Calc.kcalText(kcalNeed))
                    Muted("Hodnoty vychádzajú z nastavení v časti Núdzové zásoby.")
                }
            }
            item {
                SCard {
                    SectionTitle("Kontrolný zoznam (" + done + "/" + checklist.size + ")")
                    for (c in checklist) {
                        val checked = c in s.checked
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                store.updateSettings { st ->
                                    st.copy(checked = if (c in st.checked) st.checked - c else st.checked + c)
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                            Text(c, Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
            items(tipGroups.size) { idx ->
                val g = tipGroups[idx]
                SCard(onClick = { open = if (open == g.title) null else g.title }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            g.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(if (open == g.title) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    }
                    if (open == g.title) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                            for (t in g.tips) {
                                Text(if (t.startsWith("✔") || t.startsWith("✖")) t else "• " + t, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            item {
                Muted(
                    "Ide o všeobecné odporúčania. V skutočnej krízovej situácii sa vždy riaďte pokynmi záchranných zložiek. " +
                        "Mapa úkrytov v aplikácii nie je, pretože neexistuje jednotný verejný zoznam; informácie o úkrytoch získate na obecnom alebo mestskom úrade."
                )
            }
        }
    }
}
