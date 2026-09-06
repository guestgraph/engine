# Writing

One voice, three registers, two languages. The voice is the same wherever a text lands: it
says why before how, it claims what it can show, and it would rather be read twice than
skimmed once. The registers differ in length and shape, not in voice. The languages differ in
more than words, and the second half of this file is about that.

## Languages

Everything is written in English: pages, documentation, agent files, code and its comments,
commit messages, pull requests, release notes and what an agent says. English here is en-US;
the section on English below says what that means in spelling and marks.

The pages carry a second language, Swiss German, de-CH. It is there because the person behind
the family is Swiss, and a reader in Zürich or Bern is a reader the pages are written for. The
German is a translation of the English, made after the owner has reviewed the English and never
before, by the translator role of `TRANSLATOR.md`, in the forms Switzerland uses. It is
reviewed by reading the translator's back-translation, an English rendering of what the German
says, because that takes a minute where reading German prose takes an evening and the family
has one reader for it. It appears only where a page carries it, in the value of an attribute
whose name ends in `-de` and in the few other places a page's own agent file names, and nowhere
else: not in git, not in a reply. A third language is not planned; adding one would mean
writing this paragraph again with its own reason.

## How a text is made

A text starts from a brief that names the audience, the one point, the facts it may claim and
where each is shown, and the file and place it lands. A draft without a brief is a draft the
reviewer has to reverse-engineer, and the reviewer is the person whose time is shortest.

The writer of `WRITER.md` drafts the English on the branch, in the register the place calls
for, and reports what it wrote and which claims it could not trace to the brief or the
repository. The owner reviews on the branch, in the diff and on the rendered page, because that
is the review a pull request gets anyway and a second channel would be a second place to lose a
correction.

The translator of `TRANSLATOR.md` makes the German from the reviewed English only, one element
at a time, with `GLOSSARY.md` open, and hands back a back-translation beside each element.
German made from a draft is German that has to be made again. An English edit to an element
re-runs the translator on that element alone; nothing marks an element stale, so the rule is
kept by whoever edits the English.

Both roles edit files and neither commits. `WORKING.md` says who does.

## What every register shares

Spelling is American English, in names and in prose: organization, modeling, license, color,
behavior. Proper nouns, quoted matter and names fixed by something outside the family — a
product, a standard, a file the ecosystem reads by name — stay as they are. A tripwire in this
repository holds the rule for what it ships, and every repository that vendors this file is
expected to hold it the same way for its own text.

Cause before mechanism. A reader learns why a thing is the way it is before learning how it
works, because the why is what lets them decide whether the how still applies. A sentence that
states a mechanism with no cause is a sentence the next person will undo.

Written forward. A page, a rule or an agent file says how things are and why, in the present;
it does not narrate how they came to be. The past is in git, where a commit message says what
changed and why, and in a spec, where a decision keeps the finding that led to it. A reader who
wants the story follows the history; a reader who wants the rule does not read the story first.

Claim only what is measured or verifiable. A number is quoted after it was counted, not
estimated; a behavior is described after it was observed, not inferred from the source. Where
something was not checked, the text says so.

No adjective that sells. Nothing here is powerful, seamless, robust or elegant. If it is any of
those, the sentence that shows it is better than the word.

Closed-source predecessor projects are never mentioned, in code, in documentation, in commits
or in conversation.

## The prose register

For pages, README files, agent files, specs and release notes.

Paragraphs by default. A list only for items that are genuinely parallel, and never a
paragraph wearing a bullet. A table only for data with more than one dimension, never for
sentences. Bold at most one sentence per section, and that sentence is the rule. Headers only
where a reader would jump to, never to segment a text that fits on a screen.

Sentence case in headings and titles; a proper noun keeps its capitals. The first sentence of
a section carries the point; the rest is its support.

Release notes are this register aimed at a consumer: what changed for them, what breaks and
how to take it, in that order.

A page is this register aimed at a visitor who has not decided to stay. The first line is the
one point, in the words the visitor would use for it, and every later screen earns its place or
goes. Sentences run shorter than in a README because a page is read on a phone. A claim the
page opens with is a claim the page then shows. Nothing else changes: cause before mechanism
holds wherever a page explains, and no adjective sells.

## The git register

For commit messages and pull request descriptions.

The subject is a sentence in plain words, under seventy characters where it can be, with no
type prefix and no trailing period. It says what is now true that was not before.

The body is one to three short paragraphs, cause before mechanism. No headers, no bullets. A
table only for counts. It ends with one line beginning `Verified:` that names what ran and
passed, then the trailers.

A pull request description is the commit body reread for a reviewer who has not seen the
diff, plus links to the sibling pull requests when there are any.

One example, a commit message in full, then the trailers:

```
The list is the scope

What REPOSITORIES.md names is the family, and what it does not name is out of reach: an
agent working in a member does not read, link or reason outside the list on its own.
When a task needs something outside it, the task names it and the one purpose it
serves, and that reference stays with the task.

Verified: the spelling tripwire passes.
```

## The reply register

For what an agent says in conversation, in a review comment and in a report.

Outcome first, then what was found, then what is next. Short sentences with a verb. A list
only for parallel items, one or two sentences each. No headers under a page of text. A review
finding is one finding, with its severity and the line it sits on, and it is an input to the
person who merges, never a verdict.

## English

en-US, and the marks that go with it. Spelling and marks follow the Chicago form, with one
exception taken from AP style: the em-dash is spaced — like this — between clauses, the
newspaper form, because that is the form the family's texts are read in. Chicago's closed form
is not used. Curly quotes, “outside” and ‘inside’. No serial comma: vision, strategy and
processes. Dates read May 4, 2012, and months abbreviate to three letters without a period,
Oct 2012. A range takes a closed en-dash, May 2012–Oct 2016. Numbers group by comma, 16,000.

## German

de-CH, as the section on languages says, and not the German of Germany. The reader is Sie,
never du, except in a letter that matches its recipient. ss, never ß: Strasse, Massstab,
grösser. Guillemets, «aussen» and ‹innen›. The Gedankenstrich is a spaced en-dash – like this – and never an em-dash: German typography
has no em-dash, its dash is the Halbgeviertstrich, so the two languages share the spacing and
differ in the glyph, each following its own typography. Dates read
4. Mai 2012; abbreviated months carry their period where German abbreviates them, Jan., Febr.,
März, Apr., Mai, Juni, Juli, Aug., Sept., Okt., Nov., Dez. A range takes the spaced en-dash,
Mai 2012 – Okt. 2016. Numbers group by the typographic apostrophe, 16’000, the character that
cannot end a single-quoted attribute.

German is written as German, not as English syntax with German words: shorter sentences where
German would otherwise stack clauses, the verb where German puts it, and a noun where English
reached for a gerund.

## The same paragraph, twice

English, in the prose register:

> The rule is one line long because the attributes were built to obey it: a page's own text
> is English, and the translation is what an attribute carries — not the other way round.
> Anything that rewrites text in bulk masks those attributes first; otherwise correct German
> becomes wrong German and nothing here notices, because every check reads the rendered page,
> and the rendered page is only ever one language.

Deutsch, im selben Register:

> Die Regel ist eine Zeile lang, weil die Attribute so gebaut wurden, dass sie ihr folgen: Der
> eigene Text einer Seite ist Englisch, die Übersetzung steht im Attribut – nicht umgekehrt.
> Wer Text in grosser Menge umschreibt, maskiert zuerst diese Attribute. Sonst wird aus
> richtigem Deutsch falsches, und keine Prüfung bemerkt es, denn jede Prüfung liest die
> gerenderte Seite, und die ist immer nur in einer Sprache.
