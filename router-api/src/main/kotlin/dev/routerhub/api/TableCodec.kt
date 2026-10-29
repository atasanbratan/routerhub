package dev.routerhub.api

/**
 * Carries the rows of a [SettingField.Table] through the string-only settings contract: cells are
 * separated by TAB, rows by LF, and backslash, TAB and LF inside a cell are escaped as `\\`, `\t`
 * and `\n`.
 *
 * An empty table encodes to the empty string, so a table whose only row consists of one empty cell
 * can't be told apart from an empty table. Tables with a required first column don't have that case.
 */
public object TableCodec {
    public fun encode(rows: List<List<String>>): String =
        rows.joinToString("\n") { row -> row.joinToString("\t") { escape(it) } }

    public fun decode(encoded: String): List<List<String>> {
        if (encoded.isEmpty()) return emptyList()
        val rows = mutableListOf<List<String>>()
        var cells = mutableListOf<String>()
        val cell = StringBuilder()
        var i = 0
        while (i < encoded.length) {
            val c = encoded[i]
            when {
                c == '\\' && i + 1 < encoded.length -> {
                    i++
                    when (val next = encoded[i]) {
                        't' -> cell.append('\t')
                        'n' -> cell.append('\n')
                        else -> cell.append(next) // `\\` and any other escaped character
                    }
                }
                c == '\t' -> { cells.add(cell.toString()); cell.setLength(0) }
                c == '\n' -> {
                    cells.add(cell.toString()); cell.setLength(0)
                    rows.add(cells); cells = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        cells.add(cell.toString())
        rows.add(cells)
        return rows
    }

    private fun escape(cell: String): String = buildString {
        for (c in cell) {
            when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                else -> append(c)
            }
        }
    }
}
