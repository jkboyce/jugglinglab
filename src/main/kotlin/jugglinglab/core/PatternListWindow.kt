//
// PatternListWindow.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.JugglingLab
import jugglinglab.JugglingLab.guistrings
import jugglinglab.jml.JMLNode
import jugglinglab.jml.JMLParser
import jugglinglab.util.*
import jugglinglab.util.ErrorDialog.handleFatalException
import jugglinglab.util.ErrorDialog.handleUserException
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.io.FileNotFoundException
import java.io.FileWriter
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.text.MessageFormat
import java.util.Locale
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.max

class PatternListWindow(title: String?) : JFrame(), ActionListener {
    lateinit var patternListPanel: PatternListPanel
        private set
    var windowMenu: JMenu? = null
        private set
    private var lastJmlFilename: String? = null

    init {
        createMenus()
        createContents()
        patternListPanel.patternList.title = title
        setTitle(title)

        location = nextScreenLocation
        isVisible = true

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE)
        addWindowListener(
            object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    try {
                        doMenuCommand(MenuCommand.FILE_CLOSE)
                    } catch (je: JuggleException) {
                        handleFatalException(je)
                    }
                }
            })

        SwingUtilities.invokeLater { ApplicationWindow.updateWindowMenus() }
    }

    //--------------------------------------------------------------------------
    // Alternate constructors
    //--------------------------------------------------------------------------

    // Load from parsed JML.

    constructor(root: JMLNode?) : this("") {
        if (root != null) {
            patternListPanel.patternList.readJML(root)
            setTitle(patternListPanel.patternList.title)
        }
    }

    // Target of a (running) pattern generator.

    constructor(title: String?, gen: Thread?) : this(title) {
        if (gen != null) {
            val generator: Thread = gen
            addWindowListener(
                object : WindowAdapter() {
                    override fun windowClosing(e: WindowEvent?) {
                        try {
                            generator.interrupt()
                        } catch (_: Exception) {
                        }
                    }
                })
        }
    }

    //--------------------------------------------------------------------------
    // Methods to create and manage window contents
    //--------------------------------------------------------------------------

    private fun createContents() {
        patternListPanel = PatternListPanel(this)
        patternListPanel.isDoubleBuffered = true
        contentPane = patternListPanel

        val loc = Locale.getDefault()
        applyComponentOrientation(ComponentOrientation.getOrientation(loc))

        // list contents are always left-to-right -- DISABLE FOR NOW
        // this.getContentPane().applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        setBackground(Color.white)
        setSize(300, 450)
    }

    fun setJmlFilename(fname: String?) {
        lastJmlFilename = fname
    }

    //--------------------------------------------------------------------------
    // Menu creation and handlers
    //--------------------------------------------------------------------------

    private fun createMenus() {
        val mb = JMenuBar()
        mb.add(createFileMenu())
        this.windowMenu = JMenu(guistrings.getString("Window"))
        mb.add(this.windowMenu)
        mb.add(createHelpMenu())
        jMenuBar = mb
    }

    private fun createFileMenu(): JMenu {
        val filemenu = JMenu(guistrings.getString("File"))
        for (i in fileItems.indices) {
            if (fileItems[i] == null) {
                filemenu.addSeparator()
                continue
            }

            val fileitem = JMenuItem(guistrings.getString(fileItems[i]!!.replace(' ', '_')))

            if (fileShortcuts[i] != ' ') {
                fileitem.setAccelerator(
                    KeyStroke.getKeyStroke(
                        fileShortcuts[i].code,
                        Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
                    )
                )
            }

            fileitem.actionCommand = fileCommands[i]
            fileitem.addActionListener(this)
            filemenu.add(fileitem)
        }
        return filemenu
    }

    private fun createHelpMenu(): JMenu {
        // skip the about menu item if About handler was already installed
        // in JugglingLab.java
        val includeAbout =
            !Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.APP_ABOUT)

        var menuname: String = guistrings.getString("Help")
        // Menus titled "Help" are handled differently by macOS; only want to
        // have one of them across the entire app.
        if (JugglingLab.isMacOS) {
            menuname += ' '
        }
        val helpmenu = JMenu(menuname)

        for (i in (if (includeAbout) 0 else 1)..<helpItems.size) {
            if (helpItems[i] == null) {
                helpmenu.addSeparator()
            } else {
                val helpitem = JMenuItem(guistrings.getString(helpItems[i]!!.replace(' ', '_')))
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
                "saveas" -> doMenuCommand(MenuCommand.FILE_SAVE)
                "savetext" -> doMenuCommand(MenuCommand.FILE_SAVETEXT)
                "duplicate" -> doMenuCommand(MenuCommand.FILE_DUPLICATE)
                "changetitle" -> doMenuCommand(MenuCommand.FILE_TITLE)
                "close" -> doMenuCommand(MenuCommand.FILE_CLOSE)
                "about" -> doMenuCommand(MenuCommand.HELP_ABOUT)
                "online" -> doMenuCommand(MenuCommand.HELP_ONLINE)
            }
        } catch (je: JuggleExceptionUser) {
            handleUserException(this, je.message)
        } catch (jei: JuggleExceptionInternal) {
            handleFatalException(jei)
        }
    }

    private enum class MenuCommand {
        FILE_NONE,
        FILE_NEWPAT,
        FILE_NEWPL,
        FILE_OPEN,
        FILE_SAVE,
        FILE_SAVETEXT,
        FILE_DUPLICATE,
        FILE_TITLE,
        FILE_CLOSE,
        HELP_ABOUT,
        HELP_ONLINE,
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun doMenuCommand(action: MenuCommand) {
        when (action) {
            MenuCommand.FILE_NONE -> {}
            MenuCommand.FILE_NEWPAT -> ApplicationWindow.newPattern()
            MenuCommand.FILE_NEWPL -> PatternListWindow("").setTitle(null)
            MenuCommand.FILE_OPEN -> ApplicationWindow.openJMLFile()
            MenuCommand.FILE_SAVE -> try {
                var fname = lastJmlFilename ?: (getTitle() + ".jml")
                fname = jlSanitizeFilename(fname)
                jfc.setSelectedFile(File(fname))
                jfc.setFileFilter(FileNameExtensionFilter("JML file", "jml"))
                if (jfc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                    return
                }

                var f = jfc.selectedFile ?: return
                if (!f.absolutePath.endsWith(".jml")) {
                    f = File(f.absolutePath + ".jml")
                }
                jlErrorIfNotSanitized(f.getName())
                lastJmlFilename = f.getName()

                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR))
                val fw = FileWriter(f)
                patternListPanel.patternList.writeJML(fw)
                fw.close()
                patternListPanel.hasUnsavedChanges = false
            } catch (fnfe: FileNotFoundException) {
                throw JuggleExceptionInternal("File not found on save: " + fnfe.message)
            } catch (ioe: IOException) {
                throw JuggleExceptionInternal("IOException on save: " + ioe.message)
            } finally {
                setCursor(Cursor.getDefaultCursor())
            }

            MenuCommand.FILE_SAVETEXT -> try {
                var fname = lastJmlFilename
                if (fname != null) {
                    val index = fname.lastIndexOf(".")
                    val base = if (index >= 0) fname.take(index) else fname
                    fname = "$base.txt"
                } else {
                    fname = getTitle() + ".txt" // default filename
                }

                fname = jlSanitizeFilename(fname)
                jfc.setSelectedFile(File(fname))
                jfc.setFileFilter(FileNameExtensionFilter("Text file", "txt"))

                if (jfc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                    return
                }

                var f = jfc.selectedFile ?: return
                if (!f.absolutePath.endsWith(".txt")) {
                    f = File(f.absolutePath + ".txt")
                }
                jlErrorIfNotSanitized(f.getName())
                val index = f.getName().lastIndexOf(".")
                val base = if (index >= 0) f.getName().substring(0, index) else f.getName()
                lastJmlFilename = "$base.jml"

                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR))
                val fw = FileWriter(f)
                patternListPanel.patternList.writeText(fw)
                fw.close()
            } catch (fnfe: FileNotFoundException) {
                throw JuggleExceptionInternal("File not found on save: " + fnfe.message)
            } catch (ioe: IOException) {
                throw JuggleExceptionInternal("IOException on save: " + ioe.message)
            } finally {
                setCursor(Cursor.getDefaultCursor())
            }

            MenuCommand.FILE_DUPLICATE -> {
                val sw = StringWriter()
                patternListPanel.patternList.writeJML(sw)
                val parser = JMLParser()
                parser.parse(StringReader(sw.toString()))
                val newplw = PatternListWindow(parser.tree!!)
                newplw.patternListPanel.patternList.title = "$title copy"
                newplw.title = "$title copy"
            }

            MenuCommand.FILE_TITLE -> changeTitle()
            MenuCommand.FILE_CLOSE -> dispose()
            MenuCommand.HELP_ABOUT -> ApplicationWindow.showAboutBox()
            MenuCommand.HELP_ONLINE -> ApplicationWindow.showOnlineHelp()
        }
    }

    // Open a dialog to allow the user to change the pattern list's title.

    private fun changeTitle() {
        val jd = JDialog(this, guistrings.getString("Change_title"), true)
        val gb = GridBagLayout()
        jd.contentPane.setLayout(gb)

        val tf = JTextField(20)
        tf.text = patternListPanel.patternList.title

        val okbutton = JButton(guistrings.getString("OK"))
        okbutton.addActionListener { _: ActionEvent? ->
            val newtitle = tf.getText()
            patternListPanel.patternList.title = newtitle
            title = newtitle
            jd.dispose()
        }

        jd.contentPane.add(tf)
        gb.setConstraints(
            tf, constraints(GridBagConstraints.LINE_START, 0, 0, Insets(10, 10, 0, 10))
        )
        jd.contentPane.add(okbutton)
        gb.setConstraints(
            okbutton,
            constraints(GridBagConstraints.LINE_END, 0, 1, Insets(10, 10, 10, 10))
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

    override fun setTitle(newTitle: String?) {
        var title = newTitle
        if (title == null || title.isEmpty()) {
            title = guistrings.getString("PLWINDOW_Default_window_title")
        }
        super.setTitle(title)
        ApplicationWindow.updateWindowMenus()
    }

    //--------------------------------------------------------------------------
    // java.awt.Window methods
    //--------------------------------------------------------------------------

    override fun dispose() {
        if (patternListPanel.hasUnsavedChanges) {
            val template: String = guistrings.getString("PLWINDOW_Unsaved_changes_message")
            val arguments = arrayOf<Any?>(getTitle())
            val message = MessageFormat.format(template, *arguments)
            val title: String = guistrings.getString("PLWINDOW_Unsaved_changes_title")

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
                        handleFatalException(je)
                        return
                    }
                    if (patternListPanel.hasUnsavedChanges) {
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
        private val TILE_START: Point = Point(0, 620)
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

        private val fileItems: Array<String?> = arrayOf(
            "New Pattern",
            "New Pattern List",
            "Open JML...",
            "Save JML As...",
            "Save Text As...",
            null,
            "Duplicate",
            null,
            "Change Title...",
            null,
            "Close",
        )
        private val fileCommands: Array<String?> = arrayOf(
            "newpat",
            "newpl",
            "open",
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
            'N',
            'L',
            'O',
            'S',
            'T',
            ' ',
            'D',
            ' ',
            ' ',
            ' ',
            'W',
        )

        private val helpItems: Array<String?> = arrayOf(
            "About Juggling Lab",
            "Juggling Lab Online Help",
        )
        private val helpCommands: Array<String?> = arrayOf(
            "about",
            "online",
        )
    }
}
