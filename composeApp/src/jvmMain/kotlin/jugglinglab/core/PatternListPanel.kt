//
// PatternListPanel.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions", "SimplifyBooleanWithConstants")

package jugglinglab.core

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.jml.JMLNode
import jugglinglab.jml.JMLPatternList
import jugglinglab.jml.JMLPatternList.PatternRecord
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.jlHandleUserException
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.jlConstraints
import jugglinglab.util.getStringResource
import jugglinglab.view.View
import org.jetbrains.compose.resources.StringResource
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.*
import javax.swing.*
import javax.swing.border.BevelBorder
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import kotlin.collections.ArrayList

class PatternListPanel private constructor() : JPanel() {
    val patternList: JMLPatternList = JMLPatternList()

    private var parentFrame: JFrame? = null
    private var animTarget: View? = null
    private lateinit var list: JList<PatternRecord>
    var hasUnsavedChanges: Boolean = false

    // for mouse/popup menu handling
    private var didPopup: Boolean = false
    private var popupPatterns: ArrayList<PatternWindow>? = null
    private var dialog: JDialog? = null
    private var tf: JTextField? = null
    private var okButton: JButton? = null

    private lateinit var listModel: PatternListModel
    // for drag and drop operations
    private var draggingOut: Boolean = false

    init {
        makePanel()
        setOpaque(false)
    }

    constructor(parentFrame: JFrame?) : this() {
        this.parentFrame = parentFrame
    }

    constructor(target: View?) : this() {
        setTargetView(target)
    }

    //--------------------------------------------------------------------------
    // Methods to create and manage contents
    //--------------------------------------------------------------------------

    private fun makePanel() {
        listModel = PatternListModel()
        list = JList(listModel)
        list.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.setCellRenderer(PatternCellRenderer())

        list.setDragEnabled(true)
        list.setTransferHandler(PatternTransferHandler())

        val pane = JScrollPane(list)
        list.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(me: MouseEvent) {
                    val row = list.locationToIndex(me.getPoint())
                    if (row >= 0) {
                        list.setSelectedIndex(row)
                    }

                    didPopup = false

                    if (me.isPopupTrigger) {
                        // On macOS the popup triggers here
                        makePopupMenu().show(list, me.getX(), me.getY())
                        didPopup = true
                    }
                }

                override fun mouseReleased(me: MouseEvent) {
                    if (me.isPopupTrigger) {
                        // On Windows the popup triggers here
                        makePopupMenu().show(list, me.getX(), me.getY())
                        didPopup = true
                    }

                    if (!didPopup) {
                        launchAnimation()
                        checkSelection()
                    }
                }
            })

        setLayout(BorderLayout())
        add(pane, BorderLayout.CENTER)
    }

    // Try to launch an animation window for the currently-selected item in the
    // list. If there is no pattern associated with the line, do nothing.

    private fun launchAnimation() {
        try {
            val row = list.selectedIndex.takeIf { it >= 0 && it < listModel.size } ?: return
            if (JMLPatternList.BLANK_AT_END && row == listModel.size) {
                return
            }

            val pat = patternList.getPatternForLine(row) ?: return
            pat.layout  // do this before getting hash code
            if (PatternWindow.bringToFront(pat.jlHashCode)) {
                return
            }

            val ap = patternList.getAnimationPrefsForLine(row)

            if (animTarget != null) {
                animTarget!!.restartView(pat, ap)
            } else {
                PatternWindow(pat.title, pat, ap)
            }
        } catch (jeu: JuggleExceptionUser) {
            jlHandleUserException(this@PatternListPanel, jeu.message)
        } catch (jei: JuggleExceptionInternal) {
            jlHandleFatalException(jei)
        }
    }

    fun clearList() {
        if (listModel.size > 0) {
            hasUnsavedChanges = true
        }
        listModel.clearModel()
    }

    fun setTargetView(target: View?) {
        animTarget = target
    }

    // Used by GeneratorTarget.

    fun addPattern(display: String, animprefs: String?, notation: String?, anim: String?) {
        listModel.add(PatternRecord(display, animprefs, notation, anim, null, null, null))
    }

    // Sync the view with the underlying JMLPatternList. Used during JML load.

    fun updateView() {
        listModel.updateAll()
    }

    private fun makePopupMenu(): JPopupMenu {
        val popup = JPopupMenu()
        val row = list.selectedIndex

        val pupatterns = ArrayList<PatternWindow>()
        popupPatterns = pupatterns
        for (fr in Frame.getFrames()) {
            if (fr.isVisible && fr is PatternWindow) {
                pupatterns.add(fr)
            }
        }

        val al =
            ActionListener { ae: ActionEvent? ->
                val command = ae!!.getActionCommand()
                val row1 = list.selectedIndex
                when (command) {
                    "inserttext" -> insertText(row1)
                    "insertpattern" -> {}
                    "displaytext" -> if (row1 >= 0) changeDisplayText(row1)
                    "remove" -> if (row1 >= 0) listModel.remove(row1)
                    else -> {
                        // inserting a pattern
                        val patnum = command.substring(3).toInt()
                        val pw = pupatterns[patnum]
                        insertPattern(row1, pw)
                    }
                }
            }

        for (i in popupItems.indices) {
            val name: String? = popupItems[i]
            if (name == null) {
                popup.addSeparator()
                continue
            }

            val item = JMenuItem(getStringResource(popupItemsStringResources[i]!!))
            item.actionCommand = popupCommands[i]
            item.addActionListener(al)

            if ((popupCommands[i] == "displaytext" || popupCommands[i] == "remove") && row < 0) {
                item.setEnabled(false)
            }
            if ((popupCommands[i] == "displaytext" || popupCommands[i] == "remove")
                && JMLPatternList.BLANK_AT_END && row == listModel.size
            ) {
                item.setEnabled(false)
            }
            if (popupCommands[i] == "insertpattern" && pupatterns.isEmpty()) {
                item.setEnabled(false)
            }

            popup.add(item)

            if (popupCommands[i] == "insertpattern") {
                for ((patnum, pw) in pupatterns.withIndex()) {
                    val pitem = JMenuItem("   " + pw.getTitle())
                    pitem.actionCommand = "pat$patnum"
                    pitem.addActionListener(al)
                    pitem.setFont(FONT_PATTERN_POPUP)
                    popup.add(pitem)
                }
            }
        }

        popup.setBorder(BevelBorder(BevelBorder.RAISED))

        popup.addPopupMenuListener(
            object : PopupMenuListener {
                override fun popupMenuCanceled(e: PopupMenuEvent?) {
                    checkSelection()
                }
                override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {}
                override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {}
            })

        return popup
    }

    // Insert the pattern in the given PatternWindow into the given row.

    private fun insertPattern(row: Int, pw: PatternWindow) {
        val display = pw.getTitle()
        var animprefs: String? = pw.animationPrefs.toString()
        if (animprefs!!.isEmpty()) {
            animprefs = null
        }
        var notation: String? = "jml"
        var anim: String? = null

        val pattern = pw.pattern!!
        var patnode: JMLNode?
        try {
            patnode = pattern.rootNode!!.findNode("pattern")
        } catch (jei: JuggleExceptionInternal) {
            // any error here cannot be user error since pattern is
            // already animating in another window
            jlHandleFatalException(jei)
            return
        }
        val infonode = patnode!!.findNode("info")

        if (pattern.hasBasePattern && !pattern.isBasePatternEdited) {
            // add as base pattern instead of JML
            notation = pattern.basePatternNotation
            anim = pattern.basePatternConfig
            patnode = null
        }

        val rec = PatternRecord(
            display, animprefs, notation, anim, patnode, infonode)
        listModel.add(row, rec)

        if (row < 0) {
            if (JMLPatternList.BLANK_AT_END) {
                list.setSelectedIndex(listModel.size - 1)
            } else {
                list.setSelectedIndex(listModel.size)
            }
        } else {
            list.setSelectedIndex(row)
        }

        hasUnsavedChanges = true
    }

    // Open a dialog to allow the user to insert a line of text.

    private fun insertText(row: Int) {
        makeDialog(getStringResource(Res.string.gui_pldialog_insert_text), "")

        okButton!!.addActionListener { _: ActionEvent? ->
            val display = tf!!.getText()
            dialog!!.dispose()

            listModel.add(row, PatternRecord(display, null, null, null, null, null, null))
            if (row < 0) {
                list.setSelectedIndex(listModel.size - 1)
            } else {
                list.setSelectedIndex(row)
            }
            hasUnsavedChanges = true
        }

        dialog!!.isVisible = true
    }

    // Open a dialog to allow the user to change the display text of a line.

    private fun changeDisplayText(row: Int) {
        val rec = listModel.getElementAt(row)
        makeDialog(getStringResource(Res.string.gui_pldialog_change_display_text), rec.display)

        okButton!!.addActionListener { _: ActionEvent? ->
            rec.display = tf!!.getText()
            dialog!!.dispose()
            listModel.update(row)
            hasUnsavedChanges = true
        }

        dialog!!.isVisible = true
    }

    // Helper to make popup dialog boxes.

    private fun makeDialog(title: String?, defaultText: String?): JDialog {
        dialog = JDialog(parentFrame, title, true)
        val d = dialog!!
        val gb = GridBagLayout()
        d.contentPane.setLayout(gb)

        tf = JTextField(20)
        tf!!.text = defaultText

        okButton = JButton(getStringResource(Res.string.gui_ok))

        d.contentPane.add(tf)
        gb.setConstraints(
            tf, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(10, 10, 0, 10))
        )
        d.contentPane.add(okButton)
        gb.setConstraints(
            okButton,
            jlConstraints(GridBagConstraints.LINE_END, 0, 1, Insets(10, 10, 10, 10))
        )
        d.getRootPane().setDefaultButton(okButton) // OK button is default
        d.pack()
        d.setResizable(false)
        d.setLocationRelativeTo(this)

        d.addWindowListener(
            object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    checkSelection()
                }
            })

        return d
    }

    // Do final cleanup after any mouse-related interaction.

    private fun checkSelection() {
        if (JMLPatternList.BLANK_AT_END && list.selectedIndex == listModel.size) {
            list.clearSelection()
        }
        popupPatterns = null
        dialog = null
        tf = null
        okButton = null
    }

    //--------------------------------------------------------------------------
    // Classes to support drag and drop operations
    //--------------------------------------------------------------------------

    internal inner class PatternTransferHandler : TransferHandler() {
        override fun getSourceActions(c: JComponent?): Int {
            return COPY_OR_MOVE
        }

        override fun createTransferable(c: JComponent?): Transferable? {
            val row = list.selectedIndex
            if (row < 0 || (JMLPatternList.BLANK_AT_END && row == listModel.size)) {
                return null
            }

            draggingOut = true
            val rec = listModel.getElementAt(row)
            return PatternTransferable(rec)
        }

        override fun canImport(info: TransferSupport): Boolean {
            // support only drop (not clipboard paste)
            if (!info.isDrop) {
                return false
            }

            if (draggingOut) {
                info.setDropAction(MOVE) // within same list
            } else {
                info.setDropAction(COPY) // between lists
            }

            if (info.isDataFlavorSupported(PATTERN_FLAVOR)) {
                return true
            }
            return info.isDataFlavorSupported(DataFlavor.stringFlavor)
        }

        override fun importData(info: TransferSupport): Boolean {
            if (!info.isDrop) {
                return false
            }

            val dl = info.getDropLocation() as JList.DropLocation
            var index = dl.index
            if (index < 0) {
                index = listModel.size
            }

            // Get the record that is being dropped
            val t = info.getTransferable()

            try {
                if (t.isDataFlavorSupported(PATTERN_FLAVOR)) {
                    // Drop from another PatternListPanel
                    val rec = t.getTransferData(PATTERN_FLAVOR) as PatternRecord
                    listModel.add(index, PatternRecord(rec))
                    list.setSelectedIndex(index)
                    hasUnsavedChanges = true
                    return true
                }

                if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    val s = t.getTransferData(DataFlavor.stringFlavor) as String

                    // allow for multi-line strings
                    val lines: Array<String?> =
                        s.trimEnd().split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                    for (i in lines.indices.reversed()) {
                        val rec = PatternRecord(lines[i]!!, null, null, null, null, null, null)
                        listModel.add(index, rec)
                    }
                    list.setSelectedIndex(index)
                    hasUnsavedChanges = true
                    return true
                }
            } catch (e: Exception) {
                jlHandleFatalException(e)
            }

            return false
        }

        override fun exportDone(c: JComponent?, data: Transferable?, action: Int) {
            if (action == MOVE) {
                if (data !is PatternTransferable) {
                    return
                }

                if (!listModel.remove(data.rec)) {
                    jlHandleFatalException(JuggleExceptionInternal("PLP: exportDone()"))
                }

                hasUnsavedChanges = true
            }

            draggingOut = false
        }
    }

    internal class PatternTransferable(var rec: PatternRecord) : Transferable {
        override fun getTransferData(flavor: DataFlavor): Any {
            if (flavor.equals(PATTERN_FLAVOR)) {
                return rec
            }

            if (flavor.equals(DataFlavor.stringFlavor)) {
                return if (rec.anim == null || rec.anim!!.isEmpty()) {
                    rec.display
                } else {
                    rec.anim!!
                }
            }

            return rec
        }

        override fun getTransferDataFlavors(): Array<DataFlavor?> {
            return arrayOf(
                PATTERN_FLAVOR,
                DataFlavor.stringFlavor,
            )
        }

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
            return flavor.equals(PATTERN_FLAVOR) || flavor.equals(DataFlavor.stringFlavor)
        }
    }

    //--------------------------------------------------------------------------
    // Class to support rendering of list items
    //--------------------------------------------------------------------------

    internal class PatternCellRenderer : JLabel(), ListCellRenderer<PatternRecord?> {
        override fun getListCellRendererComponent(
            list: JList<out PatternRecord?>,  // the list
            rec: PatternRecord?,  // value to display
            index: Int,  // cell index
            isSelected: Boolean,  // is the cell selected
            cellHasFocus: Boolean
        ): Component // does the cell have focus
        {
            if (rec == null) return this
            setFont(if (rec.anim == null && rec.patnode == null) FONT_NOPATTERN else FONT_PATTERN)
            setText(if (!rec.display.isEmpty()) rec.display else " ")

            if (isSelected) {
                setBackground(list.selectionBackground)
                setForeground(list.selectionForeground)
            } else {
                setBackground(list.getBackground())
                setForeground(list.getForeground())
            }
            setEnabled(list.isEnabled)
            setOpaque(true)
            return this
        }
    }

    // This inner class acts as a bridge between the JList (View) and the
    // JMLPatternList (Model), following the MVC pattern.

    private inner class PatternListModel : AbstractListModel<PatternRecord>() {
        override fun getSize(): Int = patternList.model.size

        override fun getElementAt(index: Int): PatternRecord = patternList.model[index]

        fun add(index: Int, element: PatternRecord) {
            val insertIndex = patternList.addLine(index, element)
            fireIntervalAdded(this, insertIndex, insertIndex)
        }

        fun add(element: PatternRecord) {
            val insertIndex = patternList.addLine(-1, element)
            fireIntervalAdded(this, insertIndex, insertIndex)
        }

        fun remove(index: Int) {
            patternList.model.removeAt(index)
            fireIntervalRemoved(this, index, index)
        }

        fun remove(element: PatternRecord): Boolean {
            val index = patternList.model.indexOf(element)
            return if (index != -1) {
                remove(index)
                true
            } else false
        }

        fun update(index: Int) = fireContentsChanged(this, index, index)

        fun updateAll() = fireContentsChanged(this, 0, size)

        fun clearModel() {
            patternList.clearModel()
            fireContentsChanged(this, 0, size)
        }
    }

    companion object {
        val FONT_NOPATTERN: Font = Font("SanSerif", Font.BOLD or Font.ITALIC, 14)
        val FONT_PATTERN: Font = Font("Monospaced", Font.PLAIN, 14)
        val FONT_PATTERN_POPUP: Font = Font("Monospaced", Font.ITALIC, 14)

        val PATTERN_FLAVOR: DataFlavor = DataFlavor(PatternRecord::class.java,
            "Juggling Lab pattern record")

        //----------------------------------------------------------------------
        // Popup menu and associated handler methods
        //----------------------------------------------------------------------

        private val popupItems: List<String?> = listOf(
            "PLPOPUP Insert text...",
            null,
            "PLPOPUP Insert pattern",
            null,
            "PLPOPUP Change display text...",
            null,
            "PLPOPUP Remove line",
        )
        private val popupItemsStringResources: List<StringResource?> = listOf(
            Res.string.gui_plpopup_insert_text___,
            null,
            Res.string.gui_plpopup_insert_pattern,
            null,
            Res.string.gui_plpopup_change_display_text___,
            null,
            Res.string.gui_plpopup_remove_line,
        )
        private val popupCommands: List<String?> = listOf(
            "inserttext",
            null,
            "insertpattern",
            null,
            "displaytext",
            null,
            "remove",
        )
    }
}
