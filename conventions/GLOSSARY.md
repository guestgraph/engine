# Glossary

Every family term in its fixed English and German form. The writer uses the English column
so the third site says what the first says; the translator uses the German column so one
word has one rendering across the sites. A term joins the table the first time a text needs
it and the translator has to choose, and the owner's choice is recorded here rather than in
the attribute where it was first made.

The German cells are inline code because the prose check reads no language and a German word
such as `Organisation` would be a hit. This table, the German paragraph of `WRITING.md` and the
marks `TRANSLATOR.md` repeats are the only places the shared files carry German on purpose.

| Term | English | German | Note |
| --- | --- | --- | --- |
| CompanyGraph | CompanyGraph | `CompanyGraph` | A name; one word, two capitals, in both languages. |
| GuestGraph | GuestGraph | `GuestGraph` | A name, as above. |
| meta-model | meta-model | `Meta-Modell` | Hyphen and lower case in English; hyphen and two capitals in German. Every site carries this form. |
| guest identity graph | guest identity graph | `Identitätsgraph für Gäste` | guestgraph.io's own phrase; `Open-Source-Identitätsgraph` where the sentence says open source. |
| identity resolution | identity resolution | `Identitätsauflösung` | guestgraph.io, the billing page and the intro talk. |
| Mental Model | Mental Model | `Mental Model` | A coined name for the owner's knowledge base, kept in English in both languages like CompanyGraph; `mentales Modell` is the psychology term and is not meant. The page `/model/` is `Modell`, the generic noun. |
| connector | connector | `Connector` | The component that brings one source system into the graph. Kept English like `core`, because the repositories are named for it — `connector-apaleo` — and a reader who met a German form would have to bridge to find them. Masculine, `der Connector`; plural `die Connectors`. |
| engine | engine | `Engine` | The open core itself, the service that resolves identities and serves the API. Feminine, `die Engine`, the form the intro talk and the problems page both already use. |
| open core | open core | `Open Core` | Kept in English on blust.ch; a term of the trade. |
| open source | open source | `quelloffen` | The adjective; capitalized `Quelloffen` where it stands alone as a tag; in a compound, `Open-Source-`, as guestgraph.io writes it. Never the bare English `Open Source` in German. |
| talk | talk | `Vortrag` | A talk on a site; the deck is the file that carries it. |
| reference instance | reference instance | `Referenz-Instanz` | mental-model's role in the family; the form blust.ch's ideas page chose. |
| guest graph | guest graph | `Gast-Graph` | The short form, distinct from guest identity graph; blust.ch's ideas page. |
| core | core | `core` | The directory in companygraph/meta-model and the release its version names; a name the ecosystem reads, so not `Kern`. |
| considered, not accepted | considered, not accepted | `Erwogen, nicht angenommen` | The design system's phrase for a candidate weighed and set aside; one form on every billing page. |
| figure | figure | `Diagramm` | The drawn graph on a model page. The owner's choice over `Zeichnung`, which reads as a drawing by hand. |
| experience | experience | `Erfahrung` | An entry in the model's experiences folder, whatever its kind; blust.ch's timeline and model pages. The row it makes on the timeline is an `Eintrag`. |
| kind of experience | kind of experience | `Erfahrungsart` | The experience-kind entities — Role, Project, Community, Education, Independent — whose names stay English in both views. The owner's choice over the bare `Art` on a control, which the page's prose may still use for the short form. |
| surface | surface | `Surface` | A page the model is published on, written by hand or built by a repository's build, and the type in `model/surfaces/`. A name the ecosystem reads, so not `Oberfläche`, which in German software prose is the user interface. Capitalized as a German noun; feminine, `die Surface`, plural `die Surfaces`. |
| owner | owner | `Owner` | The one person the company of one is, and the role of that name in the model. Kept English, because the role's name on blust.ch's team page is English in both views and the word and the role are one thing. The owner's choice over `Inhaber`. Masculine, `der Owner`. |
| build | build | `Build` | What a repository runs to write a page or a built surface from the model. Kept English like `Connector`; masculine, `der Build`. |
| pin | pin | `Pin` | The visible line in which one repository takes another's release or commit; the participle is `gepinnt`. Masculine, `der Pin`. |
| commit | commit | `Commit` | A git commit, and the one a pin names. Masculine, `der Commit`; plural `die Commits`. |
| MCP | MCP | `MCP` | The protocol an agent reads a model over. A name, English in both languages, and it compounds with a hyphen. |
| MCP server | MCP server | `MCP-Server` | The server that answers over MCP; `companygraph/mcp-server` is the package. Masculine, `der MCP-Server`. |
| MCP Registry | MCP Registry | `MCP Registry` | The public index a server is published to. A proper name, English in both languages, and feminine: `in der MCP Registry`, never `im`. No page carried German for it before, which is why the gender is recorded here rather than left to the next sentence. |
| AI agent | AI agent | `KI-Agent` | The reader a model serves beside a person. Masculine, `der KI-Agent`, plural `die KI-Agenten`, the form blust.ch's ideas page already uses. |
| read-only | read-only | `nur lesend` | Of a server that answers and never writes. Not `schreibgeschützt`, which says write-protected and describes what may be done to a file rather than what a server offers. |
| shipped | shipped | `ausgeliefert` | Of a thing released and running, as against designed or built. Not `gebaut`, which says only built, and not `fertig`, which says finished. |
| entity | entity | `Entität` | One file in the model, of whatever type. The form blust.ch's model page uses. |
| type | type | `Typ` | One of the types a schema declares; plural `Typen`, and `Kerntypen` for core's own, as blust.ch's ideas page writes it. |
| claim | claim | `Anspruch` | What a profile asserts of itself and a page then has to show. blust.ch's model page: `Ein Anspruch ohne konkreten Beleg daneben ist im Modell ein Fehler`. |
| evidence | evidence | `Beleg` | The fact a claim rests on, in the cell beside it. Same sentence as above. |
| declare | declares | `deklariert` | Of what a schema states a type carries, or an instance states of itself. Not `erklärt`, which reads as explains. |

English forms fixed here whose German no page carries yet, to be chosen the first time a text
needs them: pack, design system, deck.
