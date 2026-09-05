# Repositories

Three organizations, one family. `robertblust` holds the person and the shared machinery,
`guestgraph` the guest identity graph, `companygraph` the meta-model for operating a company.
Every repository below vendors this repository's `conventions/` at a pinned release and opens
its `AGENTS.md` with the same block; `CLAUDE.md` is the same four-line vendor adapter
everywhere.

| Repository | Purpose | Default branch | Local path |
|---|---|---|---|
| robertblust/conventions | how the family writes and works, vendored by every member | main | ~/git/robertblust/conventions |
| robertblust/design | the design system shared by the three sites: tokens, chrome, page checks | main | ~/git/robertblust/design |
| robertblust/robertblust.github.io | blust.ch, the profile page and two talks | main | ~/git/robertblust/robertblust.github.io |
| robertblust/mental-model | Robert Blust described in CompanyGraph, the reference instance | main | ~/git/robertblust/mental-model |
| robertblust/field-notes | problems that took real work to understand, one file each | main | ~/git/robertblust/field-notes |
| guestgraph/guestgraph.github.io | guestgraph.io, the landing page and the intro talk | main | ~/git/guestgraph/guestgraph.github.io |
| guestgraph/engine | identity resolution, guest graph and REST API, the open core | main | ~/git/guestgraph/engine |
| guestgraph/.github | the organization profile GitHub shows, and nothing else | main | ~/git/guestgraph/.github |
| companygraph/companygraph.github.io | companygraph.io, the landing page, the model and example pages, the intro talk | main | ~/git/companygraph/companygraph.github.io |
| companygraph/meta-model | the meta-model: core vocabulary, packs and the conventions that make a graph of Markdown checkable | main | ~/git/companygraph/meta-model |
| companygraph/.github | the organization profile GitHub shows, and nothing else | main | ~/git/companygraph/.github |

## The list is the scope

What is listed here is the family; what is not listed is outside it. An agent working in a
member reads, links and reasons within this list, and does not reach for a repository, a
directory or a file outside it on its own — not for context, not for an example, not because
it sits beside a member on the same disk. When a task needs something outside the list, the
task says so, names it, and names the one purpose it serves; that reference belongs to that
task and does not bring the thing into the family.

## What pins what

The three sites pin `robertblust/design` by tag in `package.json`, and `npm run design`
writes the fenced copies. blust.ch pins `robertblust/mental-model` and companygraph.io pins
`companygraph/meta-model` by commit in `source.json`, and each builds its model pages from
that commit. blust.ch also depends on `companygraph/meta-model` by tag for the instance
parser. mental-model vendors meta-model's `core/` at a release named in its own manifest.
Every member pins this repository by tag in `conventions.json`.

A pin is an editorial line, moved on purpose. Which release each member is on is read from
the pin, never from this file, so this file does not repeat versions.

## Re-syncing after a release

In this order, one pull request each: design, then the three sites, then mental-model and
meta-model, then the engine, then field-notes, then the two `.github` repositories. Design
first because a site's suite runs design's checks; the models before the engine because the
sites' model pages are built from them. Nothing here opens those pull requests for you.
