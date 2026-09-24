# German

What Swiss Standard German asks of a page beyond the marks the German section of `WRITING.md` sets. The marks are the part a check can hold and the pages already hold them; what the pages got wrong was the German itself — English idioms carried over word for word, Germany's words where Switzerland has its own, a modal or a hedge lost in the shortening — and a back-translation cannot see any of it, because a calque reads back as the English it was copied from. This file is what the translator writes against, what the editor reads the German against, and where the owner's choices are kept so the next page does not ask again.

## Swiss words

Where Switzerland and Germany write different words, the page writes Switzerland's. The German of Germany is not wrong to a reader in Zürich, but it tells them the page was not written for them.

| Swiss | Not | Note |
| --- | --- | --- |
| `Entscheid` | `Entscheidung` | The noun for a decision taken; `Entscheidung` stays for the act of deciding. |
| `Reservation` | `Reservierung` | A booking. |
| `Spesen` | `Auslagen` | Costs billed beside a fee. |
| `Mandat` | `Auftrag`, `Fall` | An engagement of a consultant or an architect. |
| `selbständig` | `selbstständig` | The Swiss spelling. |
| `allfällig` | `etwaig` | |
| `Auftragsbearbeiter` | `Auftragsverarbeiter` | The revised DSG's term; where a page names the GDPR too, the GDPR term follows in brackets. |
| `bearbeiten` (Daten) | `verarbeiten` (Daten) | The DSG's verb for what is done with personal data. |
| `Website` | `Seite` for a site | `Seite` is one page; a site of several pages is a `Website`. |
| `per` (Datum) | `zum` (Datum) | Of a date something takes effect. |
| `innert` | `innerhalb von` | Of a period of time. |

## What the translator does by habit

Each of these was found on the pages, more than once, by a German-only read. A translator that knows the habit is a translator that can look for it.

**An English idiom taken word for word.** «Das ist die Steuer» for *that is the tax*, «ich baue beide offen» for *building both in the open*, «Das Pendel dreht» for *the pendulum swings*. German has its own idiom for most of these — «der Preis dafür», «öffentlich», «Das Pendel schlägt zurück» — and where it has none, the plain sentence is better than the copied picture.

**An English verb kept where German takes another.** *Hold* is not always «halten»: a person «besetzt» a seat, a file «enthält» its rows, a person «hat» a role «inne». *Survive* is rarely «überleben» outside a living thing: a guest ID «bleibt gültig». *Break* is «funktioniert nicht mehr», *land* is «erscheint» or «kommt an». Check every verb that carries a picture in English.

**A gerund made a noun.** «beim Folgen eines Links», «das Folgen eines Links übersteht». German takes a clause: «wenn Sie einem Link folgen».

**A modal, a hedge or a referent lost in the shortening.** *Over* twenty-five years became «fünfundzwanzig Jahre»; *cannot be left* became «wird nicht verlassen»; *what would make it usable* became «was es brauchbar macht»; *the panel* dropped out and left «ihn» pointing at nothing. German is shortened by restructuring the sentence, never by dropping a word that carries its meaning, and every pronoun names something the reader has already met.

**An English sentence frame kept for emphasis.** «Ein Pfad hier ist deshalb ein Pfad, den es gibt.» English repeats the noun to stress it; German moves the stressed word to the front or says it once: «Jeder Pfad hier existiert also tatsächlich.»

**A long English sentence kept long.** English chains clauses with commas and participles; German reads them as one breath too many. Split where the English turns, and put the verb where German expects it.

## Forms the check refuses

One form a line, the refused form, an arrow and the form the page writes. The page check reads this block from the vendored copy and fails on any refused form inside a German value, matched case-insensitively and as a whole phrase, so a form enters it only where it is wrong in every sentence it could appear in; a form that is wrong only in some sentences belongs in the tables above, where the editor weighs it.

```banned
Reservierung → Reservation
selbstständig → selbständig
Offener Kern → Open Core
Open Source → quelloffen, or Open-Source- in a compound
eure → Ihre
euch → Sie
Takeaway → Fazit
Requirements → Anforderungen
```

A bare count of the years, «fünfundzwanzig Jahre» without «über», is refused by a rule of its own in the check, because a phrase list cannot say "unless preceded by".

## The owner's choices

Each row is a sentence the editor flagged and the owner settled, kept where it generalizes: the form that was on the page, the form chosen and the reason in the owner's words where there was one. The translator reads this table before a page, so a choice made once is not asked again. Term choices are rows of `GLOSSARY.md`, not of this table.

| On the page | Chosen | Why |
| --- | --- | --- |
| «ich baue beide offen» | «ich entwickle beide öffentlich» | An idea is not built; *in the open* is «öffentlich». |
| «eine einzige Quelle der Wahrheit» | «eine einzige verbindliche Quelle» | The English picture read as a calque. |
| «Ein Konzept, erklärt» | «Ein Konzept im Überblick» | The label copied the English. |
| «Der Haken» | «Der Aufhänger» | The hook of a talk; «Haken» is a catch. |
| «wie Arbeit fliesst» | «wie die Arbeit abläuft» | The English picture of flow. |
| «Menschen richten sich schneller aus» | «Menschen finden schneller eine gemeinsame Linie» | *Align* word for word. |
