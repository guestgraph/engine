# Working

How the family acts with git and GitHub. Each rule carries its reason, because a rule without its reason is the first thing a fresh clone drops.

## Branches and commits

One branch per change, named for what it does, branched from the default branch. Nothing is committed on the default branch directly; it is protected in every repository that has a suite, and a ruleset that forbids a push is the only kind that survives a hurried afternoon.

The branch is checked out in a worktree, not in the clone. The clone stays on the default branch, so a build, an editor window or an agent reading a file there reads what is released rather than whatever a branch is halfway through, and two changes can be open at once without either one's working tree standing in the other's way. A worktree sits beside the repository it belongs to and carries its branch in its name — `git worktree add ../<repository>-<branch> -b <branch>` — because a sibling is visible in the directory listing where a hidden one is forgotten, and a name that says its branch answers what a stray directory is months later. It is removed with `git worktree remove` once its pull request is merged, together with the branch it held.

A branch is deleted once its pull request is merged. The merge commit is its record; a branch left standing is a question every reader of the branch list has to answer again.

An agent commits when the owner asks, and not on its own initiative. It proposes the message in the git register of `WRITING.md`. The author of the commit is the person. A tool that co-authored the change is named in a `Co-Authored-By` trailer, whichever tool it was, so the history says who and what wrote it. “Commit and open the pull request” is a request to do exactly that; it is not approval to merge. A role invoked as a subagent, the writer of `WRITER.md` or the translator of `TRANSLATOR.md`, edits files and reports; it never commits, and the session that invoked it proposes the message.

A commit that exists may still be reworded while its branch is unpushed — a missing `Verified:` line, a fact the body got wrong — and whoever is driving the branch does that. What a commit contains is a different thing. A finding against a task already committed is a finding like any other: it comes back as a new commit through review, never folded into the old one, because a commit rewritten after it was read is no longer the commit that was read. Once a branch is pushed and a pull request is open someone may already have it, so it is rewritten only when the owner says so, with `--force-with-lease` and never `--force`.

## Pull requests

Every change reaches the default branch through a pull request with one green status check. The description is the commit body reread for a reviewer who has not seen the diff.

**A pull request is merged with a merge commit, `gh pr merge --merge`, never squashed.** GitHub re-authors a squash commit to the account that pressed the button, so a commit made locally under the wrong identity would land on the default branch looking correct. A merge commit preserves the author it was given, which is the point: a wrong identity surfaces instead of being laundered.

Merging is a decision the owner makes. An agent opens the pull request, reports the check, and stops; it merges when told to, and the word for that is the owner's, not inferred from an earlier one.

Pull requests that depend on one another are linked as a stack, GitHub's own, with `gh stack link` from the bottom up. Each then shows only its own layer, the default branch's rules and checks hold for every layer and not only the lowest, and the one above re-targets to the default branch by itself when the one below merges; re-targeted by hand, a wrong order closes the pull request. A stack is merged from the bottom with `gh stack merge --merge`, because GitHub refuses the ordinary command for a pull request in a stack, and the merge commit rule holds there as everywhere.

When a layer merges, GitHub rebases the branches above it onto the merge and replaces them on its side. That rewrites a pushed branch without the owner's word, and it is the one rewrite that passes: the content is byte for byte what it was, the author, the committer and the message are kept, and only the commit's name changes, so nothing a reviewer read is different and no identity is laundered. Whoever drives the branch resets their worktree to the remote before adding to it. The cascade a person starts, `gh stack sync` or `gh stack rebase`, is not that. It is a rewrite like any other, and it waits for the owner's word.

## Identity

A commit is authored by the person who made it, under the address they mean to be known by, and the merge commit carries that address to the default branch unchanged. That is the whole rule, and it holds for a contributor from outside exactly as it holds for the owner; nothing here asks a contributor to be anyone but themselves.

Nothing on GitHub enforces an address, and nothing should. The ruleset rule that could, `commit_author_email_pattern`, is not available on this plan — a ruleset carrying it is rejected while an otherwise identical one carrying a `deletion` rule is accepted, tested rather than assumed — and it would shut out every outside contributor if it were. So each person's identity is their own `git config` to keep. The owner's is `robert.blust@flatland.ch` in all three organizations, keyed by `includeIf` blocks in `~/.gitconfig` to the directories `~/git/robertblust/`, `~/git/guestgraph/` and `~/git/companygraph/`; a clone made anywhere else takes the global default and gives no warning. Before the first commit in a fresh clone, whoever you are, run `git config user.email` and read the answer.

## Releases and pins

This holds for every repository in the family that another one takes from, whatever it provides: a design system, a model, a parser, a set of shared files.

A release is a tag and a GitHub Release with notes in the prose register: what changed for the consumer, what breaks, how to take it. There is no publish step anywhere in the family; the tag is the release.

Everything one repository takes from another is pinned by a visible line in the taking repository, in whatever form its tooling gives it — a tag in a package file, a commit in a source file, a release in a vendoring manifest. Pins are editorial. They move when the owner decides they move, in a commit that says why, and no bot proposes them; a pin that is behind is intent until the owner says it is drift.

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
