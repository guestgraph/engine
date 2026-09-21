// The rules the family's Markdown form needs and markdownlint does not ship. Both read only the
// tokens markdownlint hands them, so each runs the same under the CLI and inside an editor
// plugin that bundles markdownlint, and each writes its own fix rather than reporting one.
//
// markdownlint's compact table style holds the space inside each pipe but not the length of the
// delimiter row, and an editor that lines a table up writes that row as long as its widest
// cell: `table-delimiter-row` writes it back as `| --- |`, keeping each column's alignment
// colons.
//
// `paragraph-on-one-line` joins a paragraph a writer wrapped. A wrap is a decision one writer's
// editor made about where the words sit, and the next editor makes it again somewhere else, so
// a paragraph that nobody touched comes back as a diff. The rule reaches a paragraph of the
// page's own prose and nothing else: not one inside a blockquote, where a tagline is allowed
// its own line breaks, not one inside a list item, and not one carrying a hard break, where the
// break is content and joining would change what renders.
"use strict";

const delimiterRows = (tokens, found = []) => {
  for (const token of tokens) {
    if (token.type === "tableDelimiterRow") found.push(token);
    else if (token.children) delimiterRows(token.children, found);
  }
  return found;
};

const cell = (text) => {
  const t = text.trim();
  const left = t.startsWith(":");
  const right = t.endsWith(":") && t.length > 1;
  return (left ? ":" : "") + "---" + (right ? ":" : "");
};

const BLOCK_WITH_ITS_OWN_WRAPPING = new Set(["blockQuote", "listOrdered", "listUnordered"]);
const HARD_BREAK = new Set(["hardBreakTrailing", "hardBreakEscape"]);

const ownParagraphs = (tokens, found = []) => {
  for (const token of tokens) {
    if (BLOCK_WITH_ITS_OWN_WRAPPING.has(token.type)) continue;
    if (token.type === "paragraph") found.push(token);
    else if (token.children) ownParagraphs(token.children, found);
  }
  return found;
};

const hasHardBreak = (tokens) =>
  tokens.some((t) => HARD_BREAK.has(t.type) || (t.children && hasHardBreak(t.children)));


module.exports = [
  {
    names: ["table-delimiter-row"],
    description: "Table delimiter row is | --- | per column, whatever its width",
    tags: ["table"],
    parser: "micromark",
    function: (params, onError) => {
      for (const row of delimiterRows(params.parsers.micromark.tokens)) {
        const text = row.text.replace(/\s+$/, "");
        const inner = text.replace(/^\|/, "").replace(/\|$/, "");
        const want = "| " + inner.split("|").map(cell).join(" | ") + " |";
        if (text !== want) {
          onError({
            lineNumber: row.startLine,
            detail: `Expected "${want}"`,
            context: text,
            fixInfo: { editColumn: row.startColumn, deleteCount: text.length, insertText: want },
          });
        }
      }
    },
  },
  {
    names: ["paragraph-on-one-line"],
    description: "A paragraph of the page's own prose is one source line",
    tags: ["whitespace"],
    parser: "micromark",
    function: (params, onError) => {
      for (const paragraph of ownParagraphs(params.parsers.micromark.tokens)) {
        if (paragraph.endLine === paragraph.startLine) continue;
        if (hasHardBreak(paragraph.children || [])) continue;
        // The paragraph's own text, never params.lines: markdownlint blanks the inside of an
        // HTML comment there with dots so no rule fires on what a writer commented out, and a
        // fix built from those lines would write the dots back as the text.
        const lines = paragraph.text.split("\n");
        onError({
          lineNumber: paragraph.startLine,
          detail: "the paragraph goes on below; its lines belong on this one",
          context: lines[0],
          fixInfo: {
            editColumn: 1,
            deleteCount: params.lines[paragraph.startLine - 1].length,
            insertText: lines.map((line) => line.trim()).join(" "),
          },
        });
        for (let line = paragraph.startLine + 1; line <= paragraph.endLine; line += 1) {
          onError({
            lineNumber: line,
            detail: "this line continues the paragraph above",
            context: params.lines[line - 1],
            fixInfo: { deleteCount: -1 },
          });
        }
      }
    },
  },
];
