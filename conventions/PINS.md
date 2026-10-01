# Pins

What a member takes from another member of the family is a pin, and `WORKING.md` says why a pin is a tag or a commit. This file says how a member declares its pins so that the family resync in robertblust/conventions can read and move them: a report that says which pins are behind, and a run that moves the ones the owner chooses through to merged pull requests and releases.

## The kinds

| Kind | The line | How it moves |
| --- | --- | --- |
| `conventions` | `tag` in `conventions.json` | set the tag, `sh conventions/conventions-sync sync`, and every `robertblust/conventions/.github/workflows/check.yml@<tag>` line under `.github/workflows/` |
| `service-conventions` | `tag` in `service-conventions.json` | set the tag, `sh service-conventions/service-conventions-sync sync` |
| `npm-tag` | `github:owner/repo#tag` in a `package.json` | set the tag, `npm install` |
| `source-commit` | an object with `repo` and `commit` in a JSON file, at the top or under a key | set the commit of the object whose `repo` matches |
| `contract-commit` | a string `owner/repo@commit:path` in a JSON file | set the commit of every string for that repository |
| `core-release` | `tooling` in `.companygraph/manifest.json`, the meta-model release an instance took with its core | the entry's own `move` command, with `{version}` that release without its `v` |

## `pins.json`

A member declares its pins in `pins.json` at its root. The report reads every pin a member's files hold, declared or not, and shows a pin the file does not declare as unmanaged and never moves it. The file is a member's consent to be moved, and the commands in it are how the member rebuilds itself after a move.

```json
{
  "pins": [
    { "kind": "conventions", "file": "conventions.json", "repo": "robertblust/conventions" },
    { "kind": "npm-tag", "file": "package.json", "repo": "robertblust/design", "after": ["npm run design"] },
    { "kind": "source-commit", "file": "source.json", "repo": "robertblust/mental-model", "after": ["npm run model", "npm run pages"] }
  ],
  "verify": ["npm test"],
  "release": ["npm version {version} --no-git-tag-version"]
}
```

`pins` lists the pins. Each names its `kind`, the `file` that holds the line and the `repo` it points at. `after` lists the commands that run in the member once that pin has moved, in order. `move`, a command with `{version}` in it, replaces the kind's own move, and a `core-release` pin must give one. `watch` lists paths in the upstream for a commit pin, which is then behind only when a newer commit touches one of them; a `contract-commit` pin watches the paths its strings name without being told.

`verify` lists the commands that run once every pin of the member has moved, and a failing one blocks the member. A check in `verify`, `npm run <name>:check`, fails as soon as a move changes what its writer `npm run <name>` writes, so the report names a check whose writer no pin's `after` or `move` runs. `release` lists the commands that run when the resync releases the member, with `{version}` standing for the new version without its `v`; they leave the bump uncommitted, and the run commits it. A member that nothing takes by tag has no `release`.

The report names every entry that does not match a line in the file it names, so a `pins.json` that has fallen behind its member is seen the first time the report runs.
