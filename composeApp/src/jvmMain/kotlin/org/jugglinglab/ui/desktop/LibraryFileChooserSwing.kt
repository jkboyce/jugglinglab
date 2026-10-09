//
// LibraryFileChooserSwing.kt
//
// Swing UI component for selecting and opening built-in JML pattern lists.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.desktop

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.jml.JmlParser
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.ui.common.BuiltinPatternEntry
import org.jugglinglab.ui.common.BuiltinPatternLists
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlGetStringResource
import org.jugglinglab.util.jlHandleUserException
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class LibraryListItem {
    data class Header(val title: String) : LibraryListItem()
    data class Entry(val entry: BuiltinPatternEntry) : LibraryListItem()
}

class LibraryFileChooserSwing(
    private val coroutineScope: CoroutineScope
) : JPanel(BorderLayout()) {
    private val listModel = DefaultListModel<LibraryListItem>()
    private val list = JList(listModel)

    init {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = LibraryCellRenderer()

        // Populate items
        listModel.addElement(
            LibraryListItem.Header(
                jlGetStringResource(Res.string.gui_mobile_filechooser_pattern_library)
            )
        )
        for (entry in BuiltinPatternLists.basic) {
            listModel.addElement(LibraryListItem.Entry(entry))
        }

        listModel.addElement(
            LibraryListItem.Header(
                jlGetStringResource(Res.string.gui_mobile_filechooser_other)
            )
        )
        for (entry in BuiltinPatternLists.other) {
            listModel.addElement(LibraryListItem.Entry(entry))
        }

        // Single-click to open
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(me: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(me)) {
                    val index = list.locationToIndex(me.point)
                    if (index >= 0) {
                        val bounds = list.getCellBounds(index, index)
                        if (bounds != null && bounds.contains(me.point)) {
                            val item = listModel.getElementAt(index)
                            if (item is LibraryListItem.Entry) {
                                list.clearSelection()
                                openBuiltinPattern(item.entry)
                            }
                        }
                    }
                }
            }
        })

        // Enter key to open
        list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(ke: KeyEvent) {
                if (ke.keyCode == KeyEvent.VK_ENTER) {
                    val selected = list.selectedValue
                    if (selected is LibraryListItem.Entry) {
                        list.clearSelection()
                        openBuiltinPattern(selected.entry)
                    }
                }
            }
        })

        // Hand cursor when hovering over clickable entries
        list.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(me: MouseEvent) {
                val index = list.locationToIndex(me.point)
                if (index >= 0) {
                    val bounds = list.getCellBounds(index, index)
                    if (bounds != null && bounds.contains(me.point)) {
                        val item = listModel.getElementAt(index)
                        if (item is LibraryListItem.Entry) {
                            list.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                            return
                        }
                    }
                }
                list.cursor = Cursor.getDefaultCursor()
            }
        })

        val scrollPane = JScrollPane(list).apply {
            border = null
        }
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun openBuiltinPattern(entry: BuiltinPatternEntry) {
        coroutineScope.launch(Dispatchers.Default) {
            try {
                val bytes = Res.readBytes("files/${entry.filename}")
                val text = bytes.decodeToString()
                val parser = JmlParser()
                parser.parse(text)

                withContext(Dispatchers.Main) {
                    when (parser.fileType) {
                        JmlParser.JML_LIST -> {
                            val pl = JmlPatternList(parser.tree)
                            if (pl.title == null) {
                                pl.title = entry.displayName
                            }
                            if (!PatternListWindow.bringToFront(pl.jlHashCode)) {
                                PatternListWindow(patternList = pl)
                            }
                        }

                        JmlParser.JML_PATTERN -> {
                            val pat = JmlPattern.fromJmlNode(parser.tree!!)
                            pat.layout
                            if (!PatternWindow.bringToFront(pat.jlHashCode)) {
                                PatternWindow(pat.title, pat, AnimationPrefs())
                            }
                        }

                        else -> {
                            val message = jlGetStringResource(Res.string.error_invalid_jml)
                            jlHandleUserException(this@LibraryFileChooserSwing, message)
                        }
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    val message = if (t is JuggleExceptionUser) t.message ?: "Unknown error"
                    else t.message ?: "Unknown error"
                    val prefix =
                        jlGetStringResource(Res.string.error_reading_file, entry.displayName)
                    jlHandleUserException(this@LibraryFileChooserSwing, "$prefix:\n$message")
                }
            }
        }
    }

    private class LibraryCellRenderer : ListCellRenderer<LibraryListItem> {
        private val headerLabel = JLabel().apply {
            isOpaque = true
            border = EmptyBorder(12, 12, 6, 12)
        }

        private val entryLabel = JLabel().apply {
            isOpaque = true
            border = EmptyBorder(6, 20, 6, 12)
        }

        override fun getListCellRendererComponent(
            list: JList<out LibraryListItem>,
            value: LibraryListItem?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            when (value) {
                is LibraryListItem.Header -> {
                    headerLabel.text = value.title
                    headerLabel.font = list.font.deriveFont(Font.BOLD, list.font.size2D + 2f)
                    headerLabel.background = list.background
                    headerLabel.foreground = list.foreground
                    return headerLabel
                }

                is LibraryListItem.Entry -> {
                    entryLabel.text = value.entry.displayName
                    entryLabel.font = list.font.deriveFont(Font.PLAIN, list.font.size2D + 1f)
                    if (isSelected) {
                        entryLabel.background = list.selectionBackground
                        entryLabel.foreground = list.selectionForeground
                    } else {
                        entryLabel.background = list.background
                        entryLabel.foreground = list.foreground
                    }
                    return entryLabel
                }

                null -> {
                    entryLabel.text = ""
                    return entryLabel
                }
            }
        }
    }
}
