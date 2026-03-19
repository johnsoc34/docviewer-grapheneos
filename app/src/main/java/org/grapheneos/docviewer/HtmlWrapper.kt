package org.grapheneos.docviewer

object HtmlWrapper {

    fun wrap(title: String, bodyContent: String, extraCss: String = ""): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${escapeHtml(title)}</title>
<style>
/* Reset */
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

/* Base */
html {
    font-size: 15px;
    -webkit-text-size-adjust: 100%;
}
body {
    font-family: 'Roboto', -apple-system, BlinkMacSystemFont, sans-serif;
    line-height: 1.6;
    color: #1a1a1a;
    background: #ffffff;
    padding: 16px;
    word-wrap: break-word;
    overflow-wrap: break-word;
}

/* Typography */
h1, h2, h3, h4, h5, h6 {
    margin: 1em 0 0.5em 0;
    line-height: 1.3;
    color: #111;
}
h1 { font-size: 1.6rem; }
h2 { font-size: 1.35rem; }
h3 { font-size: 1.15rem; }
h4, h5, h6 { font-size: 1rem; }

p {
    margin: 0.5em 0;
}

/* Tables */
table {
    border-collapse: collapse;
    width: 100%;
    margin: 1em 0;
    font-size: 0.9rem;
    overflow-x: auto;
    display: block;
}
th, td {
    border: 1px solid #d0d0d0;
    padding: 8px 10px;
    text-align: left;
    vertical-align: top;
    white-space: pre-wrap;
}
th {
    background: #f0f2f5;
    font-weight: 600;
    color: #333;
}
tr:nth-child(even) td {
    background: #fafbfc;
}

/* Lists */
ul, ol {
    margin: 0.5em 0 0.5em 1.5em;
}
li {
    margin: 0.2em 0;
}

/* Images */
img {
    max-width: 100%;
    height: auto;
}

/* Code */
code, pre {
    font-family: 'Roboto Mono', monospace;
    font-size: 0.85rem;
    background: #f5f5f5;
    border-radius: 3px;
}
code { padding: 2px 4px; }
pre {
    padding: 12px;
    overflow-x: auto;
    margin: 0.5em 0;
}

/* Slides (for PPTX) */
.slide {
    border: 1px solid #e0e0e0;
    border-radius: 8px;
    padding: 20px;
    margin: 16px 0;
    background: #fff;
    box-shadow: 0 1px 3px rgba(0,0,0,0.08);
    page-break-inside: avoid;
}
.slide-number {
    font-size: 0.75rem;
    color: #999;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin-bottom: 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid #f0f0f0;
}
.slide h1 { font-size: 1.4rem; margin-top: 0; }
.slide h2 { font-size: 1.2rem; }

/* Spreadsheet-specific */
.sheet-tabs {
    display: flex;
    gap: 8px;
    margin-bottom: 16px;
    flex-wrap: wrap;
}
.sheet-tab {
    background: #f0f2f5;
    padding: 6px 14px;
    border-radius: 4px;
    font-size: 0.85rem;
    font-weight: 500;
    color: #555;
}
.sheet-tab.active {
    background: #1a1a2e;
    color: #fff;
}
.sheet-section {
    margin-bottom: 24px;
}
.sheet-name {
    font-size: 1.1rem;
    font-weight: 600;
    color: #333;
    margin-bottom: 8px;
    padding-bottom: 4px;
    border-bottom: 2px solid #1a1a2e;
}

/* Dark mode support */
@media (prefers-color-scheme: dark) {
    body { background: #121212; color: #e0e0e0; }
    h1, h2, h3, h4, h5, h6 { color: #f0f0f0; }
    th { background: #2a2a2a; color: #e0e0e0; }
    td { border-color: #404040; }
    tr:nth-child(even) td { background: #1a1a1a; }
    .slide { background: #1e1e1e; border-color: #333; box-shadow: 0 1px 3px rgba(0,0,0,0.3); }
    .slide-number { color: #888; border-bottom-color: #333; }
    .sheet-tab { background: #2a2a2a; color: #ccc; }
    .sheet-tab.active { background: #4a6fa5; }
    .sheet-name { border-bottom-color: #4a6fa5; }
    code, pre { background: #2a2a2a; }
}

$extraCss
</style>
</head>
<body>
$bodyContent
</body>
</html>
        """.trimIndent()
    }

    fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
