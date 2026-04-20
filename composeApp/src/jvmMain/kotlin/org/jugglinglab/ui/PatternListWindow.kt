//
// PatternListWindow.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.Constants
import org.jugglinglab.jml.JmlParser
import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.util.JuggleException
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlConstraints
import org.jugglinglab.util.jlErrorIfNotSanitized
import org.jugglinglab.util.jlGetStringResource
import org.jugglinglab.util.jlHandleFatalException
import org.jugglinglab.util.jlHandleUserException
import org.jugglinglab.util.jlIsMacOs
import org.jugglinglab.util.jlJfc
import org.jugglinglab.util.jlBaseFileDirectory
import org.jugglinglab.util.jlSanitizeFilepath
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.io.FileNotFoundException
import java.io.FileWriter
import java.io.IOException
import java.io.StringWriter
import java.nio.file.Path
import java.util.Locale
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.max
import org.jetbrains.compose.resources.StringResource

class PatternListWindow(
    windowTitle: String? = null,
    val patternList: JmlPatternList = JmlPatternList(),
    var generatorThread: Thread? = null
) : JFrame(), ActionListener {
    val patternListPanel = PatternListPanel(
        patternList = patternList,
        parentFrame = this
    )

    var windowMenu: JMenu? = null
        private set

    val jlHashCode: Int
        get() = patternList.jlHashCode

    private var lastJmlFilepath: Path? = null
    private var lastCleanJlHashCode: Int = 0

    init {
        createMenus()
        createContents()
        if (windowTitle != null) {
            patternList.title = windowTitle
        }
        setTitle(patternList.title)
        if (generatorThread != null) {
            setTitle("$title (running)")
        }
        setContentsClean()
        location = nextScreenLocation
        isVisible = true

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE)
        addWindowListener(
            object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    try {
                        doMenuCommand(MenuCommand.FILE_CLOSE)
                    } catch (je: JuggleException) {
                        jlHandleFatalException(je)
                    }
                }
            })

        SwingUtilities.invokeLater { ApplicationWindow.updateWindowMenus() }
    }

    //--------------------------------------------------------------------------
    // Methods to create and manage window contents
    //--------------------------------------------------------------------------

    private fun createContents() {
        patternListPanel.isDoubleBuffered = true
        contentPane = patternListPanel

        val loc = Locale.getDefault()
        applyComponentOrientation(ComponentOrientation.getOrientation(loc))

        // list contents are always left-to-right -- DISABLE FOR NOW
        // this.getContentPane().applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        setBackground(Color.white)
        setSize(300, 450)
    }

    fun setJmlFilepath(fpath: Path?) {
        lastJmlFilepath = fpath
    }

    private fun setContentsClean() {
        lastCleanJlHashCode = jlHashCode
    }

    fun onGeneratorDone() {
        setContentsClean()
        setTitle(patternList.title)
        generatorThread = null
    }

    //--------------------------------------------------------------------------
    // Menu creation and handlers
    //--------------------------------------------------------------------------

    private fun createMenus() {
        val mb = JMenuBar()
        mb.add(createFileMenu())
        windowMenu = JMenu(jlGetStringResource(Res.string.gui_window))
        mb.add(windowMenu)
        mb.add(createHelpMenu())
        jMenuBar = mb
    }

    private fun createFileMenu(): JMenu {
        val fileMenu = JMenu(jlGetStringResource(Res.string.gui_file))
        for ((i, fileResource) in fileItemsStringResources.withIndex()) {
            if (fileResource == null) {
                fileMenu.addSeparator()
                continue
            }

            val fileItem = JMenuItem(jlGetStringResource(fileResource))

            if (fileShortcuts[i] != ' ') {
                val fileShortcut = fileShortcuts[i]
                var mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
                if (fileShortcut.isUpperCase()) {
                    mask = mask or java.awt.event.InputEvent.SHIFT_DOWN_MASK
                }
                fileItem.setAccelerator(
                    KeyStroke.getKeyStroke(
                        fileShortcut.uppercaseChar().code,
                        mask
                    )
                )
            }

            fileItem.actionCommand = fileCommands[i]
            fileItem.addActionListener(this)
            fileMenu.add(fileItem)
        }
        return fileMenu
    }

    private fun createHelpMenu(): JMenu {
        // skip the about menu item if About handler was already installed
        // in JugglingLab.java
        val includeAbout =
            !Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.APP_ABOUT)

        var menuname: String = jlGetStringResource(Res.string.gui_help)
        // Menus titled "Help" are handled differently by macOS; only want to
        // have one of them across the entire app.
        if (jlIsMacOs()) {
            menuname += ' '
        }
        val helpmenu = JMenu(menuname)

        for (i in (if (includeAbout) 0 else 1)..<helpItems.size) {
            if (helpItems[i] == null) {
                helpmenu.addSeparator()
            } else {
                val helpitem = JMenuItem(jlGetStringResource(helpItemsStringResources[i]!!))
                helpitem.actionCommand = helpCommands[i]
                helpitem.addActionListener(this)
                helpmenu.add(helpitem)
            }
        }
        return helpmenu
    }

    override fun actionPerformed(ae: ActionEvent) {
        try {
            when (ae.getActionCommand()) {
                "newpat" -> doMenuCommand(MenuCommand.FILE_NEWPAT)
                "newpl" -> doMenuCommand(MenuCommand.FILE_NEWPL)
                "open" -> doMenuCommand(MenuCommand.FILE_OPEN)
                "save" -> doMenuCommand(MenuCommand.FILE_SAVE)
                "saveas" -> doMenuCommand(MenuCommand.FILE_SAVEAS)
                "savetext" -> doMenuCommand(MenuCommand.FILE_SAVETEXT)
                "duplicate" -> doMenuCommand(MenuCommand.FILE_DUPLICATE)
                "changetitle" -> doMenuCommand(MenuCommand.FILE_TITLE)
                "close" -> doMenuCommand(MenuCommand.FILE_CLOSE)
                "about" -> doMenuCommand(MenuCommand.HELP_ABOUT)
                "online" -> doMenuCommand(MenuCommand.HELP_ONLINE)
            }
        } catch (je: JuggleExceptionUser) {
            jlHandleUserException(this, je.message)
        } catch (jei: JuggleExceptionInternal) {
            jlHandleFatalException(jei)
        }
    }

    enum class MenuCommand {
        FILE_NONE,
        FILE_NEWPAT,
        FILE_NEWPL,
        FILE_OPEN,
        FILE_SAVE,
        FILE_SAVEAS,
        FILE_SAVETEXT,
        FILE_DUPLICATE,
        FILE_TITLE,
        FILE_CLOSE,
        HELP_ABOUT,
        HELP_ONLINE,
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun doMenuCommand(action: MenuCommand) {
        when (action) {
            MenuCommand.FILE_NONE -> {}
            MenuCommand.FILE_NEWPAT -> ApplicationWindow.newPattern()
            MenuCommand.FILE_NEWPL -> PatternListWindow("").setTitle(null)
            MenuCommand.FILE_OPEN -> ApplicationWindow.openJmlFile()
            MenuCommand.FILE_SAVE -> {
                if (lastJmlFilepath == null) {
                    doMenuCommand(MenuCommand.FILE_SAVEAS)
                } else {
                    try {
                        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR))
                        val fw = FileWriter(lastJmlFilepath!!.toFile())
                        patternList.writeJml(fw)
                        fw.close()
                        setContentsClean()
                    } catch (e: Exception) {
                        throw JuggleExceptionInternal("Exception on save: " + e.message)
                    } finally {
                        setCursor(Cursor.getDefaultCursor())
                    }
                }
            }

            MenuCommand.FILE_SAVEAS -> try {
                var fpath = lastJmlFilepath ?: jlBaseFileDirectory.resolve("${title}.jml")
                fpath = jlSanitizeFilepath(fpath)
                jlJfc.setSelectedFile(fpath.toFile())
                jlJfc.setFileFilter(FileNameExtensionFilter("JML file", "jml"))
                if (jlJfc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                    return
                }

                var f = jlJfc.selectedFile ?: return
                if (!f.absolutePath.endsWith(".jml")) {
                    f = File(f.absolutePath + ".jml")
                }
                jlErrorIfNotSanitized(f.getName())
                if (lastJmlFilepath == null && patternList.title == null) {
                    patternList.title = f.getName().substringBeforeLast(".")
                    setTitle(patternList.title)
                }
                lastJmlFilepath = f.toPath()

                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR))
                val fw = FileWriter(f)
                patternList.writeJml(fw)
                fw.close()
                setContentsClean()
            } catch (fnfe: FileNotFoundException) {
                throw JuggleExceptionInternal("File not found on save: " + fnfe.message)
            } catch (ioe: IOException) {
                throw JuggleExceptionInternal("IOException on save: " + ioe.message)
            } finally {
                setCursor(Cursor.getDefaultCursor())
            }

            MenuCommand.FILE_SAVETEXT -> try {
                var fpath = lastJmlFilepath?.let {
                    it.resolveSibling("${it.fileName.toString().substringBeforeLast(".")}.txt")
                } ?: jlBaseFileDirectory.resolve("${title}.txt")
                fpath = jlSanitizeFilepath(fpath)
                jlJfc.setSelectedFile(fpath.toFile())
                jlJfc.setFileFilter(FileNameExtensionFilter("Text file", "txt"))
                if (jlJfc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                    return
                }

                var f = jlJfc.selectedFile ?: return
                if (!f.absolutePath.endsWith(".txt")) {
                    f = File(f.absolutePath + ".txt")
                }
                jlErrorIfNotSanitized(f.getName())

                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR))
                val fw = FileWriter(f)
                patternList.writeText(fw)
                fw.close()
                setContentsClean()
            } catch (fnfe: FileNotFoundException) {
                throw JuggleExceptionInternal("File not found on save: " + fnfe.message)
            } catch (ioe: IOException) {
                throw JuggleExceptionInternal("IOException on save: " + ioe.message)
            } finally {
                setCursor(Cursor.getDefaultCursor())
            }

            MenuCommand.FILE_DUPLICATE -> {
                val pl = run {
                    val sw = StringWriter()
                    patternList.writeJml(sw)
                    val parser = JmlParser()
                    parser.parse(sw.toString())
                    JmlPatternList(jmlNode = parser.tree)
                }
                pl.title = "$title copy"
                PatternListWindow(patternList = pl)
            }

            MenuCommand.FILE_TITLE -> changeTitle()
            MenuCommand.FILE_CLOSE -> dispose()
            MenuCommand.HELP_ABOUT -> ApplicationWindow.showAboutBox()
            MenuCommand.HELP_ONLINE -> ApplicationWindow.showOnlineHelp()
        }
    }

    // Open a dialog to allow the user to change the pattern list's title.

    private fun changeTitle() {
        val jd = JDialog(this, jlGetStringResource(Res.string.gui_change_title), true)
        val gb = GridBagLayout()
        jd.contentPane.setLayout(gb)

        val tf = JTextField(20)
        tf.text = patternList.title

        val okbutton = JButton(jlGetStringResource(Res.string.gui_ok))
        okbutton.addActionListener { _: ActionEvent? ->
            val newTitle = tf.getText()
            patternList.title = newTitle
            setTitle(newTitle)
            jd.dispose()
        }

        jd.contentPane.add(tf)
        gb.setConstraints(
            tf, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(10, 10, 0, 10))
        )
        jd.contentPane.add(okbutton)
        gb.setConstraints(
            okbutton,
            jlConstraints(GridBagConstraints.LINE_END, 0, 1, Insets(10, 10, 10, 10))
        )
        jd.getRootPane().setDefaultButton(okbutton)  // OK button is default
        jd.pack()
        jd.setResizable(false)
        jd.setLocationRelativeTo(this)
        jd.isVisible = true
    }

    //--------------------------------------------------------------------------
    // java.awt.Frame methods
    //--------------------------------------------------------------------------

    override fun setTitle(title: String?) {
        var newTitle = title
        if (newTitle.isNullOrEmpty()) {
            newTitle = jlGetStringResource(Res.string.gui_plwindow_default_window_title)
        }
        super.setTitle(newTitle)
        ApplicationWindow.updateWindowMenus()
    }

    //--------------------------------------------------------------------------
    // java.awt.Window methods
    //--------------------------------------------------------------------------

    override fun dispose() {
        if (generatorThread != null) {
            generatorThread?.interrupt()
            setContentsClean()
            generatorThread = null
        }

        if (lastCleanJlHashCode != jlHashCode) {
            val message =
                jlGetStringResource(Res.string.gui_plwindow_unsaved_changes_message, getTitle())
            val title = jlGetStringResource(Res.string.gui_plwindow_unsaved_changes_title)

            val res = JOptionPane.showConfirmDialog(
                patternListPanel, message, title, JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
            )
            when (res) {
                JOptionPane.CANCEL_OPTION -> return
                JOptionPane.YES_OPTION -> {
                    try {
                        doMenuCommand(MenuCommand.FILE_SAVE)
                    } catch (je: JuggleException) {
                        jlHandleFatalException(je)
                        return
                    }
                    if (lastCleanJlHashCode != jlHashCode) {
                        return  // user canceled out of save dialog
                    }
                }

                else -> {}
            }
        }

        super.dispose()
        SwingUtilities.invokeLater { ApplicationWindow.updateWindowMenus() }
    }

    companion object {
        // used for tiling the windows on the screen as they're created
        private const val NUM_TILES: Int = 8
        private val TILE_START: Point = Point(0, 580)
        private val TILE_OFFSET: Point = Point(25, 25)
        private var tileLocations: ArrayList<Point> = ArrayList()
        private var nextTileNum: Int = 0

        private val nextScreenLocation: Point
            get() {
                if (tileLocations.isEmpty()) {
                    val center = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint()
                    val locx = max(0, center.x - Constants.RESERVED_WIDTH_PIXELS / 2)
                    for (i in 0..NUM_TILES) {
                        val locX: Int = locx + TILE_START.x + i * TILE_OFFSET.x
                        val locY: Int = TILE_START.y + i * TILE_OFFSET.y
                        tileLocations.add(Point(locX, locY))
                    }
                    nextTileNum = 0
                }

                val loc = tileLocations[nextTileNum]
                if (++nextTileNum == NUM_TILES) {
                    nextTileNum = 0
                }
                return loc
            }

        // Check if a given list is already loaded, and if so then bring that
        // window to the front.
        //
        // Returns true if the list was found, false if not.

        fun bringToFront(hash: Int): Boolean {
            for (fr in getFrames()) {
                if (fr is PatternListWindow && fr.isVisible && fr.jlHashCode == hash) {
                    SwingUtilities.invokeLater { fr.toFront() }
                    return true
                }
            }
            return false
        }

        private val fileItemsStringResources: List<StringResource?> = listOf(
            Res.string.gui_new_pattern,
            Res.string.gui_new_pattern_list,
            Res.string.gui_open_jml___,
            Res.string.gui_save_jml,
            Res.string.gui_save_jml_as___,
            Res.string.gui_save_text_as___,
            null,
            Res.string.gui_duplicate,
            null,
            Res.string.gui_change_title___,
            null,
            Res.string.gui_close,
        )
        private val fileCommands: List<String?> = listOf(
            "newpat",
            "newpl",
            "open",
            "save",
            "saveas",
            "savetext",
            null,
            "duplicate",
            null,
            "changetitle",
            null,
            "close",
        )
        private val fileShortcuts: CharArray = charArrayOf(
            'n',
            'l',
            'o',
            's',
            'S',
            't',
            ' ',
            'd',
            ' ',
            ' ',
            ' ',
            'w',
        )

        private val helpItems: List<String?> = listOf(
            "About Juggling Lab",
            "Juggling Lab Online Help",
        )
        private val helpItemsStringResources: List<StringResource?> = listOf(
            Res.string.gui_about_juggling_lab,
            Res.string.gui_juggling_lab_online_help,
        )
        private val helpCommands: List<String?> = listOf(
            "about",
            "online",
        )
    }
}
