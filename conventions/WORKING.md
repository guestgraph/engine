# Working

How the family acts with git and GitHub. Each rule carries its reason, because a rule without its reason is the first thing a fresh clone drops.

## Branches and commits

One branch per change, named for what it does, branched from the default branch. Nothing is committed on the default branch directly; it is protected in every repository that has a suite, and a ruleset that forbids a push is the only kind that survives a hurried afternoon.

The branch is checked out in a worktree, not in the clone. The clone stays on the default branch, so a build, an editor window or an agent reading a file there reads what is released rather than whatever a branch is halfway through, and two changes can be open at once without either one's working tree standing in the other's way. A worktree sits beside the repository it belongs to and carries its branch in its name — `git worktree add ../<repository>-<branch> -b <branch>` — because a sibling is visible in the directory listing where a hidden one is forgotten, and a name that says its branch answers what a stray directory is months later. It is removed with `git worktree remove` once its pull request is merged, together with the branch it held.

A branch is deleted once its pull request is merged. The merge commit is its record; a branch left standing is a question every reader of the branch list has to answer again.

An agent commits when the owner asks, and not on its own initiative. It proposes the message in the git register of `WRITING.md`. A commit the owner makes is authored by the owner. A commit an agent makes is authored by the seat it held, the role whose work the commit is, at the governing instance's domain — `git commit --author "Implementer <implementer@blust.ch>"` — and the person running the agent stays the committer, so the author says which seat did the work and the committer who is accountable for it being there. The governing instance is the repository's own where it is one, and otherwise its organization's. A specification is the Specifier's, a plan the Planner's, a task, a re-pin, a re-sync and a release bump the Implementer's, English prose the Writer's, its German the Translator's and a narrated clip the Narrator's; the Controller writes nothing and authors no commit. The message opens its trailers with `Process`, `Phase` and `Track`, as `WRITING.md` shows. `conventions/hooks/commit-msg` refuses a seat the phase in the trailers does not list, and the pull request's check refuses it again. `conventions-sync` points `core.hooksPath` at that hook, and `core.hooksPath` is local config that a clone does not carry, so a fresh clone runs `sh conventions/conventions-sync sync` once before its first commit. Until the family tooling authors them as the Implementer, the commits a resync run makes carry the person's name. A tool that co-authored the change is named in a `Co-Authored-By` trailer, whichever tool it was, so the history says who and what wrote it. “Commit and open the pull request” is a request to do exactly that; it is not approval to merge. A role invoked as a subagent — the writer of `WRITER.md`, the translator of `TRANSLATOR.md`, the editor of `EDITOR.md` or the back-reader of `BACKREADER.md` — edits files or only reports, as its own file says; it never commits, and the session that invoked it proposes the message and commits it as the seat whose work it is: the Writer's for the writer's English, the Translator's for the translator's German and the editor's corrections to it.

A commit that exists may still be reworded while its branch is unpushed — a missing `Verified:` line, a fact the body got wrong — and whoever is driving the branch does that. What a commit contains is a different thing. A finding against a task already committed is a finding like any other: it comes back as a new commit through review, never folded into the old one, because a commit rewritten after it was read is no longer the commit that was read. Once a branch is pushed and a pull request is open someone may already have it, so it is rewritten only when the owner says so, with `--force-with-lease` and never `--force`.

## Pull requests

Every change reaches the default branch through a pull request with one green status check. The description is the commit body reread for a reviewer who has not seen the diff.

**A pull request is merged with a merge commit, `gh pr merge --merge`, never squashed.** GitHub re-authors a squash commit to the account that pressed the button, so a commit made locally under the wrong identity would land on the default branch looking correct. A merge commit preserves the author it was given, which is the point: a wrong identity surfaces instead of being laundered.

Merging is a decision the owner makes. An agent opens the pull request, reports the check, and stops; it merges when told to, and the word for that is the owner's, not inferred from an earlier one.

A family resync is the one run that merges on its own. When the owner chooses chains from the family report, that choice is their word for every merge, release and deploy the run needs to carry those pins through, and the run asks nothing more. It stops wherever a person's judgment is needed by blocking the member — a failed step or suite, a check that does not pass, a clone without an address, or work on the default branch that no release describes — and holds everything downstream of it. It never releases work whose notes a person has not written, and it never releases conventions.

Pull requests that depend on one another are linked as a stack, GitHub's own, with `gh stack link` from the bottom up. Each then shows only its own layer, the default branch's rules and checks hold for every layer and not only the lowest, and the one above re-targets to the default branch by itself when the one below merges; re-targeted by hand, a wrong order closes the pull request. A stack is merged from the bottom with `gh stack merge --merge`, because GitHub refuses the ordinary command for a pull request in a stack, and the merge commit rule holds there as everywhere.

When a layer merges, GitHub rebases the branches above it onto the merge and replaces them on its side. That rewrites a pushed branch without the owner's word, and it is the one rewrite that passes: the content is byte for byte what it was, the author, the committer and the message are kept, and only the commit's name changes, so nothing a reviewer read is different and no identity is laundered. Whoever drives the branch resets their worktree to the remote before adding to it. The cascade a person starts, `gh stack sync` or `gh stack rebase`, is not that. It is a rewrite like any other, and it waits for the owner's word.

## Identity

A person's commit is authored by the person who made it, under the address they mean to be known by, and the merge commit carries that address to the default branch unchanged. That is the whole rule for a person's commit, and it holds for a contributor from outside exactly as it holds for the owner; nothing here asks a contributor to be anyone but themselves. An agent's commit is authored by its seat, as Branches and commits says.

Nothing on GitHub enforces an address, and nothing should. The ruleset rule that could, `commit_author_email_pattern`, is not available on this plan — a ruleset carrying it is rejected while an otherwise identical one carrying a `deletion` rule is accepted, tested rather than assumed — and it would shut out every outside contributor if it were. The seat check does not change that: it refuses a seat its phase does not list and lets every other author through, so it is a check on seats and not on a person's address. So each person's identity is their own `git config` to keep. The owner's is `robert@blust.ch` in all three organizations, the address the identity carries in `robertblust/mental-model`, set by `includeIf` blocks in `~/.gitconfig` that read `~/.gitconfig-blust` for the directories `~/git/robertblust/`, `~/git/guestgraph/` and `~/git/companygraph/`; a clone made anywhere else takes the global default and gives no warning. Before the first commit in a fresh clone, whoever you are, run `git config user.email` and read the answer.

## Releases and pins

This holds for every repository in the family that another one takes from, whatever it provides: a design system, a model, a parser, a set of shared files.

A release is a tag and a GitHub Release with notes in the prose register: what changed for the consumer, what breaks, how to take it. There is no publish step anywhere in the family; the tag is the release.

Everything one repository takes from another is pinned by a visible line in the taking repository, in whatever form its tooling gives it — a tag in a package file, a commit in a source file, a release in a vendoring manifest. Pins are editorial. They move when the owner decides they move, in a commit that says why, and no bot proposes them; a pin that is behind is intent until the owner says it is drift.

What is taken decides the form of the pin. Code or rules the taking repository runs or is checked against are pinned by tag, because the release is what carries the notes and the minor or major an update depends on: a parser, a design system, a schema vendored as `core/`, these files. Content it draws or serves is pinned by commit, because content changes with every edit and promises nothing a release could state, and the pin only chooses which state is shown: a mental model on a site or behind an MCP host, an OpenAPI file on a page. One repository can be taken both ways, and meta-model is: companygraph.io runs its parser at a tag and draws its `example/` and `core/` at a commit. One pin breaks the rule. The Apaleo connector implements contracts that live in the engine, and the engine has no releases, so the connector pins them by commit; once the engine releases, that pin becomes a tag.

A change to anything another repository vendors or builds from is at least a minor release, because it makes every copy stale. A change that asks the taking repository to do anything beyond re-syncing or re-pinning is a major. The notes say which.

## Checks

Verification is running the suite, not reading the diff. Nothing is called done, fixed or passing until the command that proves it has run and its output has been read; a pipe into `tail` hides an exit code, so the exit code is checked on its own.

A branch ruleset requires a status check by its job id, not by the workflow's name. Renaming the job leaves the ruleset requiring a name that will never report again: the branch looks protected and is not. Each repository names its required job id in its own agent file; rename one only together with its ruleset.

Every member's ruleset requires the `conventions` job beside the job that runs its own suite; a repository without a suite requires it alone. That job holds the vendored copy against its release and the repository's own Markdown against `WRITING.md`, and it is the same job everywhere because it is called from one place. The Markdown form is the one part of it that is not shell: `conventions-format` runs markdownlint at the version it pins, on Node, because that is a library an editor plugin can bundle too, and a form held by one tool in CI and another in the editor is two forms.

CI never writes what the repository commits. Rendered cards, exported PDFs and generated pages are built locally and committed; CI checks that the committed copy matches what would be built.

## Reviews

A review finding is an input to the person who merges, never a verdict. One finding per comment, with a severity and the line it sits on. Silence is a valid answer to a finding.

## What is never written

Closed-source predecessor projects are not mentioned — in code, documentation, commits, pull requests, issues or release notes. Secrets are never printed, not to check them and not in a debug line; a value that reaches a transcript has to be rotated.
