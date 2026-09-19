// The rules the family's Markdown form needs and markdownlint does not ship. markdownlint's
// compact table style holds the space inside each pipe but not the length of the delimiter
// row, and an editor that lines a table up writes that row as long as its widest cell: this
// rule writes it back as `| --- |`, keeping each column's alignment colons. It reads only the
// tokens markdownlint hands it, so it runs the same under the CLI and inside an editor plugin
// that bundles markdownlint.
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
];
