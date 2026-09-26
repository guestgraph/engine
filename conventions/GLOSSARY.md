# Glossary

Every family term in its fixed English and German form. The writer uses the English column so the third site says what the first says; the translator uses the German column so one word has one rendering across the sites. A term joins the table the first time a text needs it and the translator has to choose, and the owner's choice is recorded here rather than in the attribute where it was first made.

The German cells are inline code because the prose check reads no language and a German word such as `Organisation` would be a hit. This table, `GERMAN.md`, the German paragraph of `WRITING.md` and the examples the role files quote are the only places the shared files carry German on purpose.

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
| lineage | lineage | `Herkunft` | The drawing on a Surfaces page from the model's commit through each maker to its surfaces. Not `Stammbaum`, which reads as family descent, and not `Linie`, which the same page uses for the drawn line itself. companygraph.io's label is `Herkunft der Surfaces`. |
| owner | owner | `Owner` | The one person the company of one is, and the role of that name in the model. Kept English, because the role's name on blust.ch's team page is English in both views and the word and the role are one thing. The owner's choice over `Inhaber`. Masculine, `der Owner`. |
| requestor | Requestor | `Requestor` | The seat that raises a feature request, and the role of that name in companygraph/mental-model. Kept English, as the model's role names are on a team page; masculine, `der Requestor`, like `der Owner`. |
| contributor | Contributor | `Contributor` | The seat that proposes a contribution, and the role of that name in companygraph/mental-model. Kept English for the same reason; masculine, `der Contributor`. |
| build | build | `Build` | What a repository runs to write a page or a built surface from the model. Kept English like `Connector`; masculine, `der Build`. |
| pin | pin | `Pin` | The visible line in which one repository takes another's release or commit; the participle is `gepinnt`. Masculine, `der Pin`. |
| commit | commit | `Commit` | A git commit, and the one a pin names. Masculine, `der Commit`; plural `die Commits`. |
| feature request | feature request | `Feature-Request` | A request raised as a GitHub issue, and the process of that name in companygraph/mental-model. Hyphenated as German compounds an English term; masculine, `der Feature-Request`, plural `die Feature-Requests`. |
| pull request | pull request | `Pull-Request` | GitHub's name for a proposed change. Masculine, `der Pull-Request`, plural `die Pull-Requests`. |
| GitHub issue | GitHub issue | `GitHub-Issue` | GitHub's name for an issue. Neuter, `das GitHub-Issue`, plural `die GitHub-Issues`. |
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
| Swiss Standard German | Swiss Standard German | `Schweizer Hochdeutsch` | The written German of Switzerland, de-CH, the second language every page carries. Never `Swiss German`, which in English names the spoken dialect, `Schweizerdeutsch`, and which no page carries; an agent reading that name concludes the pages are in dialect. Neuter, `das Schweizer Hochdeutsch`. |
| site | site | `Website` | A site of several pages. `Seite` is one page of it, and a sentence about the whole site that says `Seite` contradicts itself on a page whose own text is English. |
| page | page | `Seite` | One page of a site. |
| company | company | `Firma` | The owner's choice over `Unternehmen`, feminine, `die Firma`. `Unternehmensführung` stays, a fixed term for the discipline rather than a word for one company. |
| company of one | company of one | `Ein-Personen-Firma` | One compound, as German writes it, not the phrase `Firma aus einer Person`. |
| decision | decision | `Entscheid` | A decision taken, the Swiss form. `Entscheidung` stays for the act of deciding, as in `Entscheidungshilfe`. |
| release | release | `Release` | A tagged release. Kept English like `Build` and `Commit`, and neuter, `das Release`; `Freigabe` is a gate's approval and is not meant. |
| deck | deck | `Präsentation` | The file a talk is given from. Feminine, `die Präsentation`; the talk itself is `Vortrag`. |
| meter | meter | `Abrechnungsgrösse` | The one unit a bill is computed from. Not `Zähler`, which reads as a device. |
| career break | career break | `Auszeit` | Alone, not `berufliche Auszeit`; the sentence around it says it is from work. |
| role | role | `Rolle` | A position held, as the Role kind is. Never `Stelle`, which is the employment itself. |
| independent period | independent period | `Phase der Selbständigkeit` | The prose around the Independent kind, whose name stays English in both views. |
| standard | standard | `Massstab` | A yardstick. Not `Anspruch`, which is this table's claim. |
| takeaway | takeaway | `Fazit` | The label that closes a talk's argument. |
| Software Engineer & Architect | Software Engineer & Architect | `Software Engineer & Architect` | The owner's title, English in both views, as Swiss IT titles usually are; in a sentence, `Software Engineer und Architect`. |
| seat (billed) | seat | `Nutzer` | What billing does not count: «Nicht pro Nutzer». Not `Sitzplatz`, a seat in a theater; a seat a person holds on a team page stays `Sitz`. |
| retainer | retainer | `Pauschalhonorar` | A flat fee paid ahead of the work. |
| day rate | rate | `Tagessatz` | What is billed per day. Not the bare `Satz`, which is also a sentence. |
| CLI | CLI | `CLI` | The command-line tool, `die CLI`, even where the English says command line. |
| chat panel | panel | `Chatfenster` | The chat's panel on a page. |
| maker | maker | `Erzeuger` | What writes a surface, a person or a build. Not `Urheber`, which carries copyright. |
| Direction | Direction | `Ausrichtung` | The group over vision, values and strategy. Not `Richtung`, a heading on a map. |
| kind | kind | `Art` | The kind of an entity. `Typ` stays for what a schema declares, so the two words keep two meanings. |
| person (in a seat) | person | `Mensch` | The human who holds a seat, set against an agent: «ein Mensch wacht über jedes Gate». An ordinary person stays `Person`. |
| board | board | `Übersicht` | A team page's grid of seats. Not `Tafel`, a blackboard. |
| projection | projection | `Projektion` | The model as a derived view of how a company runs. |
| adoption | adoption | `Verbreitung` | A thing others take up: «wenn es sich durchsetzt … seine Verbreitung». |
| process (data) | process | `bearbeiten` | The DSG's verb, for content as well as personal data, on a page that names the DSG. |
| problem detail | problem detail | `Problem Detail` | The body an API returns for a refusal, RFC 9457. The RFC's name, neuter: `das Problem Detail`. |
| actor claim | actor claim | `Actor-Claim` | A claim in a JWT that names who acts. Masculine, `der Actor-Claim`. |
| steward | steward | `verantwortliche Person` | Who reviews a proposed match; plural `die Mitarbeitenden, die prüfen`. A hotel reader does not know Steward. |
| API (surface) | surface | `API` | An API as what a caller reaches, `die API`. Not `Schnittstelle`, and not `Surface`, which is a page the model is published on. |
| credit meter | credit meter | `Abrechnung nach Guthaben` | Billing against a prepaid balance. |
| backfill | backfill | `Historischer Import` | The first import of the history before live data. |
| walk-in | walk-in | `Walk-in` | A guest without a reservation, as Swiss hotels say it; plural `Walk-ins`. |
| origin | origin | `Domain` | Where a request comes from. `Origin` is exact and unread outside a developer. |
| removal | removal | `Löschung` | A request to remove; what it removed is `entfernt`. |
| layered confidence | layered confidence | `mehrstufige Konfidenz` | Graded confidence in a match. Not `Gewissheit`, which claims certainty. |
| loyalty id | loyalty id | `Mitgliedsnummer des Treueprogramms` | A guest's loyalty number. Not `Kundennummer`, a PMS customer number. |
| review queue | review queue | `Prüfwarteschlange` | The matches a person decides. Feminine. |
| golden profile | golden profile | `das goldene Profil` | The one resolved profile of a guest. |
| matching evidence | evidence | `Hinweise` | What points toward a match. The table's `Beleg` stays the fact a claim rests on. |
| contractor rate | contractor rate | `Freelancer-Ansatz` | What an external specialist bills per hour; plural `Freelancer-Ansätze`, with the Swiss `Ansatz` for a rate. What is billed per day stays `Tagessatz`. |
| conventional | conventional | `konventionell` | Of a team working without agents, as against agentic AI. The owner's choice over `klassisch`, which judges. |
| owner's share | owner's share | `Pensum des Owners` | The part of a team's months the owner works, as a percentage; `Pensum` is the Swiss word for a share of full time. |
| delivery team | delivery team | `Umsetzungsteam` | The team that would build a result. Not `Lieferteam`, which reads as logistics. |
| workstream | workstream | `Arbeitspaket` | One slice of the work a team's effort is estimated by. |
| employer cost | employer cost | `Arbeitgeberkosten` | A salary with its social charges and workplace, what a person costs the company that employs them. |
| rating | outcome | `Wertung` | The column that says which side comes out ahead. Not `Ausgang`, which can read as exit. |
| four-eyes principle | two people on every change | `Vier-Augen-Prinzip` | German's fixed term for every change checked by a second person. |
| put up for scrutiny | put up for scrutiny | `zur Prüfung gestellt` | Of an idea opened to other people's judgment; blust.ch's ideas page and the deciding-well talk. `Prüfung` is kept for scrutiny, so the next row is not `prüfen`. |
| validate | validate | `validieren` | Of testing an idea or a proposal in the open; the noun is `Validierung`, as on blust.ch's ideas page. |
| agentic AI | agentic AI | `agentische KI` | AI that acts through agents; lower case adjective, `KI` as German abbreviates it. |
| IT architect | IT architect | `IT-Architekt` | The owner's role from October 2026; hyphenated as German writes it. |
| lead engineer | lead engineer | `Lead Engineer` | A job title, English in both languages as Swiss IT titles usually are; `Lead` alone for the lead role. |

English forms fixed here whose German no page carries yet, to be chosen the first time a text needs them: pack, design system.
