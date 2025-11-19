//
// PatternWindow.kt
//
// This class is the window that contains juggling animations. The animation
// itself is rendered by the View object.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions")

package jugglinglab.core

import jugglinglab.JugglingLab
import jugglinglab.JugglingLab.guistrings
import jugglinglab.JugglingLab.errorstrings
import jugglinglab.jml.JMLPattern
import jugglinglab.prop.Prop
import jugglinglab.util.*
import jugglinglab.util.ErrorDialog.handleFatalException
import jugglinglab.util.ErrorDialog.handleUserException
import jugglinglab.view.*
import java.awt.*
import java.awt.event.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileWriter
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.*
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.max
import kotlin.system.exitProcess

class PatternWindow(title: String?, pat: JMLPattern, jc: AnimationPrefs?) : JFrame(title),
    ActionListener {
    private lateinit var view: View
    private lateinit var colorsMenu: JMenu
    private lateinit var viewMenu: JMenu
    lateinit var windowMenu: JMenu
        private set

    private var undo: ArrayList<JMLPattern> = ArrayList()
    private var lastJmlFilename: String? = null

    init {
        loadOptimizer()  // do this before creating menus
        createMenus()
        createInitialView(pat, jc)
        view.addToUndoList(pat)

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

        addMouseWheelListener { mwe: MouseWheelEvent? ->
            mwe!!.consume() // or it triggers twice
            try {
                if (mwe.getWheelRotation() > 0) {
                    // scrolling up -> zoom in
                    doMenuCommand(MenuCommand.VIEW_ZOOMIN)
                } else if (mwe.getWheelRotation() < 0) {
                    doMenuCommand(MenuCommand.VIEW_ZOOMOUT)
                }
            } catch (je: JuggleException) {
                handleFatalException(je)
            }
        }

        SwingUtilities.invokeLater { ApplicationWindow.updateWindowMenus() }
    }

    // Create a new PatternWindow with the same JMLPattern and default View as
    // an existing PatternWindow.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private constructor(pw: PatternWindow) : this(
        pw.getTitle(),
        JMLPattern(pw.view.pattern!!),
        AnimationPrefs(pw.view.animationPrefs)
    )

    //--------------------------------------------------------------------------
    // Methods to create and manage window contents
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun createInitialView(pat: JMLPattern, jc: AnimationPrefs?) {
        val jc = jc ?: AnimationPrefs()
        val mode = when {
            (jc.view != View.VIEW_NONE) -> jc.view
            (pat.numberOfJugglers > LadderDiagram.MAX_JUGGLERS) ->
                View.VIEW_SIMPLE

            else -> View.VIEW_EDIT
        }
        //viewMode = mode
        viewMenu.getItem(mode - 1).setSelected(true)

        val animsize = Dimension(jc.width, jc.height)

        when (mode) {
            View.VIEW_NONE -> {}
            View.VIEW_SIMPLE -> view = SimpleView(animsize)
            View.VIEW_EDIT -> view = EditView(animsize, pat)
            View.VIEW_PATTERN -> view = PatternView(animsize)
            View.VIEW_SELECTION -> view = SelectionView(animsize)
        }

        view.patternWindow = this
        view.setOpaque(true)
        view.isDoubleBuffered = true
        contentPane = view

        val loc = Locale.getDefault()
        applyComponentOrientation(ComponentOrientation.getOrientation(loc))
        setBackground(Color.white)
        pack()

        view.restartView(pat, jc)
        view.setUndoList(undo, -1)
    }

    @set:Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    var viewMode: Int
        get() = when (view) {
            is SimpleView -> View.VIEW_SIMPLE
            is EditView -> View.VIEW_EDIT
            is PatternView -> View.VIEW_PATTERN
            is SelectionView -> View.VIEW_SELECTION
            else -> View.VIEW_NONE
        }
        set(mode) {
            // `mode` is one of the View.VIEW_X constants.
            viewMenu.getItem(mode - 1).setSelected(true)

            // items to carry over from old view to the new
            val pat = view.pattern!!
            val jc = view.animationPrefs
            val paused = view.isPaused
            val undoIndex = view.undoIndex
            val animsize = Dimension(jc.width, jc.height)

            val newview: View = when (mode) {
                View.VIEW_SIMPLE -> SimpleView(animsize)
                View.VIEW_EDIT -> EditView(animsize, pat)
                View.VIEW_PATTERN -> PatternView(animsize)
                View.VIEW_SELECTION -> SelectionView(animsize)
                else -> throw JuggleExceptionInternal("setViewMode: problem creating view")
            }

            newview.patternWindow = this
            newview.isPaused = paused
            newview.setOpaque(true)
            newview.isDoubleBuffered = true
            contentPane = newview

            if (isWindowMaximized) validate() else pack()

            newview.restartView(pat, jc)
            newview.setUndoList(undo, undoIndex)

            view.disposeView()
            view = newview
        }

    val pattern: JMLPattern?
        get() = view.pattern

    val animationPrefs: AnimationPrefs
        get() = view.animationPrefs

    // Used for testing whether a given JMLPattern is already being animated.
    private val hashCode: Int
        get() = view.hashCode

    // For determining if the current window is maximized in the UI.
    val isWindowMaximized: Boolean
        get() = ((extendedState and MAXIMIZED_BOTH) != 0)

    fun setJMLFilename(fname: String?) {
        lastJmlFilename = fname
    }

    //--------------------------------------------------------------------------
    // Menu creation and handlers
    //--------------------------------------------------------------------------

    private fun createMenus() {
        val mb = JMenuBar()
        mb.add(createFileMenu())
        mb.add(createViewMenu())
        windowMenu = JMenu(guistrings.getString("Window"))
        mb.add(windowMenu)
        mb.add(createHelpMenu())
        jMenuBar = mb
    }

    private fun createFileMenu(): JMenu {
        val fileMenu = JMenu(guistrings.getString("File"))
        for (i in fileItems.indices) {
            if (fileItems[i] == null) {
                fileMenu.addSeparator()
                continue
            }

            if (fileCommands[i] == "colorprops") {
                colorsMenu = JMenu(guistrings.getString(fileItems[i]!!.replace(' ', '_')))
                colorsMenu.add(JMenuItem(guistrings.getString("PCMENU_mixed")).apply {
                    actionCommand = "colors_mixed"
                    addActionListener(this@PatternWindow)
                })
                colorsMenu.add(JMenuItem(guistrings.getString("PCMENU_orbits")).apply {
                    actionCommand = "colors_orbits"
                    addActionListener(this@PatternWindow)
                })
                colorsMenu.addSeparator()
                for (colorName in Prop.COLOR_NAMES) {
                    colorsMenu.add(JMenuItem(guistrings.getString("PCMENU_$colorName")).apply {
                        actionCommand = "colors_$colorName"
                        addActionListener(this@PatternWindow)
                    })
                }
                fileMenu.add(colorsMenu)
                continue
            }

            val fileItem = JMenuItem(guistrings.getString(fileItems[i]!!.replace(' ', '_')))
            if (fileShortcuts[i] != ' ') {
                fileItem.setAccelerator(
                    KeyStroke.getKeyStroke(
                        fileShortcuts[i].code,
                        Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
                    )
                )
            }
            fileItem.actionCommand = fileCommands[i]
            fileItem.addActionListener(this)
            fileMenu.add(fileItem)

            if (fileCommands[i] == "optimize" && optimizer == null) {
                fileItem.setEnabled(false)
            }
        }
        return fileMenu
    }

    // Enable or disable the "Color Props" menu depending on whether the pattern
    // is colorable. Call this when the pattern changes.

    fun updateColorsMenu() {
        colorsMenu.isEnabled = (view.pattern?.isColorable ?: false)
    }

    private fun createViewMenu(): JMenu {
        viewMenu = JMenu(guistrings.getString("View"))
        val buttonGroup = ButtonGroup()
        var addingviews = true

        for (i in viewItems.indices) {
            if (viewItems[i] == null) {
                viewMenu.addSeparator()
                addingviews = false
                continue
            }

            if (addingviews) {
                val viewitem =
                    JRadioButtonMenuItem(guistrings.getString(viewItems[i]!!.replace(' ', '_')))

                if (viewShortcuts[i] != ' ') {
                    viewitem.setAccelerator(
                        KeyStroke.getKeyStroke(
                            viewShortcuts[i].code,
                            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
                        )
                    )
                }

                viewitem.actionCommand = viewCommands[i]
                viewitem.addActionListener(this)
                viewMenu.add(viewitem)
                buttonGroup.add(viewitem)
            } else {
                val viewitem = JMenuItem(guistrings.getString(viewItems[i]!!.replace(' ', '_')))

                if (viewShortcuts[i] != ' ') {
                    viewitem.setAccelerator(
                        KeyStroke.getKeyStroke(
                            viewShortcuts[i].code,
                            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
                        )
                    )
                }

                viewitem.actionCommand = viewCommands[i]
                viewitem.addActionListener(this)
                viewMenu.add(viewitem)
            }
        }
        return viewMenu
    }

    fun updateUndoMenu() {
        val undoIndex = view.undoIndex
        val undoEnabled = (undoIndex > 0)
        val redoEnabled = (undoIndex < undo.size - 1)

        for (i in 0..<viewMenu.itemCount) {
            val jmi = viewMenu.getItem(i)
            if (jmi == null || jmi.getActionCommand() == null) {
                continue
            }

            if (jmi.getActionCommand() == "undo") {
                jmi.setEnabled(undoEnabled)
            } else if (jmi.getActionCommand() == "redo") {
                jmi.setEnabled(redoEnabled)
            }
        }
    }

    private fun createHelpMenu(): JMenu {
        // skip the about menu item if About handler was already installed
        // in JugglingLab.java
        val includeAbout =
            !Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.APP_ABOUT)

        var menuname: String = guistrings.getString("Help")
        if (JugglingLab.isMacOS) {
            // Menus titled "Help" are handled differently by macOS; only want
            // to have one of them across the entire app.
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
                "close" -> doMenuCommand(MenuCommand.FILE_CLOSE)
                "saveas" -> doMenuCommand(MenuCommand.FILE_SAVE)
                "savegifanim" -> doMenuCommand(MenuCommand.FILE_GIFSAVE)
                "duplicate" -> doMenuCommand(MenuCommand.FILE_DUPLICATE)
                "changetitle" -> doMenuCommand(MenuCommand.FILE_TITLE)
                "changetiming" -> doMenuCommand(MenuCommand.FILE_RESCALE)
                "optimize" -> doMenuCommand(MenuCommand.FILE_OPTIMIZE)
                "swaphands" -> doMenuCommand(MenuCommand.FILE_SWAPHANDS)
                "invertx" -> doMenuCommand(MenuCommand.FILE_INVERTX)
                "inverttime" -> doMenuCommand(MenuCommand.FILE_INVERTTIME)
                "restart" -> doMenuCommand(MenuCommand.VIEW_RESTART)
                "prefs" -> doMenuCommand(MenuCommand.VIEW_ANIMPREFS)
                "undo" -> doMenuCommand(MenuCommand.VIEW_UNDO)
                "redo" -> doMenuCommand(MenuCommand.VIEW_REDO)
                "zoomin" -> doMenuCommand(MenuCommand.VIEW_ZOOMIN)
                "zoomout" -> doMenuCommand(MenuCommand.VIEW_ZOOMOUT)
                "simple" -> {
                    if (viewMode != View.VIEW_SIMPLE) {
                        viewMode = View.VIEW_SIMPLE
                    }
                }

                "visual_edit" -> {
                    if (viewMode != View.VIEW_EDIT) {
                        viewMode = View.VIEW_EDIT
                    }
                }

                "pattern_edit" -> {
                    if (viewMode != View.VIEW_PATTERN) {
                        viewMode = View.VIEW_PATTERN
                    }
                }

                "selection_edit" -> {
                    if (viewMode != View.VIEW_SELECTION) {
                        viewMode = View.VIEW_SELECTION
                    }
                }

                "about" -> doMenuCommand(MenuCommand.HELP_ABOUT)
                "online" -> doMenuCommand(MenuCommand.HELP_ONLINE)
                else -> {
                    val command = ae.getActionCommand()
                    if (!command.startsWith("colors_"))
                        return
                    val colorString = when (val colorName = command.substring(7)) {
                        "mixed" -> "mixed"
                        "orbits" -> "orbits"
                        else -> "{$colorName}"
                    }
                    try {
                        val newpat = JMLPattern(view.pattern!!)
                        newpat.setPropColors(colorString)
                        view.restartView(newpat, null)
                        view.addToUndoList(newpat)
                    } catch (_: JuggleExceptionUser) {
                        throw JuggleExceptionInternal("Error in FILE_PROPCOLORS")
                    }
                }
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
        FILE_CLOSE,
        FILE_SAVE,
        FILE_GIFSAVE,
        FILE_DUPLICATE,
        FILE_TITLE,
        FILE_RESCALE,
        FILE_OPTIMIZE,
        FILE_SWAPHANDS,
        FILE_INVERTX,
        FILE_INVERTTIME,
        VIEW_RESTART,
        VIEW_ANIMPREFS,
        VIEW_UNDO,
        VIEW_REDO,
        VIEW_ZOOMIN,
        VIEW_ZOOMOUT,
        HELP_ABOUT,
        HELP_ONLINE,
    }

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun doMenuCommand(action: MenuCommand) {
        when (action) {
            MenuCommand.FILE_NONE -> {}
            MenuCommand.FILE_NEWPAT -> ApplicationWindow.newPattern()
            MenuCommand.FILE_NEWPL -> (PatternListWindow("")).setTitle(null)
            MenuCommand.FILE_OPEN -> ApplicationWindow.openJMLFile()
            MenuCommand.FILE_CLOSE -> {
                dispose()
                if (exitOnLastClose) {
                    var windowCount = 0
                    for (fr in getFrames()) {
                        if (fr is PatternWindow && fr.isVisible) {
                            ++windowCount
                        }
                    }
                    if (windowCount == 0) {
                        exitProcess(0)
                    }
                }
            }

            MenuCommand.FILE_SAVE -> {
                if (!view.pattern!!.isValid) {
                    throw JuggleExceptionUser(errorstrings.getString("Error_saving_invalid_pattern"))
                }

                var fname = lastJmlFilename
                if (fname == null) {
                    fname = getTitle() + ".jml"  // default filename
                }
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
                try {
                    val fw = FileWriter(f)
                    view.pattern!!.writeJML(fw, writeTitle = true, writeInfo = true)
                    fw.close()
                } catch (fnfe: FileNotFoundException) {
                    throw JuggleExceptionInternal("FileNotFound: " + fnfe.message)
                } catch (ioe: IOException) {
                    throw JuggleExceptionInternal("IOException: " + ioe.message)
                }
            }

            MenuCommand.FILE_GIFSAVE -> {
                var fname = lastJmlFilename
                if (fname != null) {
                    val index = fname.lastIndexOf(".")
                    val base = if (index >= 0) fname.take(index) else fname
                    fname = "$base.gif"
                } else {
                    fname = "${getTitle()}.gif"  // default filename
                }

                fname = jlSanitizeFilename(fname)
                jfc.setSelectedFile(File(fname))
                jfc.setFileFilter(FileNameExtensionFilter("GIF file", "gif"))

                if (jfc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                    return
                }

                var f = jfc.selectedFile ?: return
                if (!f.absolutePath.endsWith(".gif")) {
                    f = File(f.absolutePath + ".gif")
                }
                jlErrorIfNotSanitized(f.getName())
                val index = f.getName().lastIndexOf(".")
                val base = if (index >= 0) f.getName().substring(0, index) else f.getName()
                lastJmlFilename = "$base.jml"
                view.writeGIF(f)
            }

            MenuCommand.FILE_DUPLICATE -> {
                val newpw = PatternWindow(this)
                newpw.pattern?.title = "$title copy"
                newpw.title = "$title copy"
            }

            MenuCommand.FILE_TITLE -> changeTitle()
            MenuCommand.FILE_RESCALE -> changeTiming()
            MenuCommand.FILE_OPTIMIZE -> {
                if (Constants.DEBUG_OPTIMIZE) {
                    println("-------------------------------------------")
                    println("optimizing in PatternWindow.doMenuCommand()")
                }
                if (optimizer == null) return
                try {
                    val optimize: Method = optimizer!!.getMethod("optimize", JMLPattern::class.java)
                    val pat = view.pattern
                    val newPat = optimize.invoke(null, pat) as JMLPattern
                    view.restartView(newPat, null)
                    view.addToUndoList(newPat)
                } catch (jeu: JuggleExceptionUser) {
                    throw JuggleExceptionInternal("optimizer jeu: ${jeu.message}")
                } catch (ite: InvocationTargetException) {
                    // exceptions thrown by Optimizer.optimize() land here
                    val ex: Throwable = ite.cause!!
                    if (Constants.DEBUG_OPTIMIZE) {
                        println("ite: ${ex.message}")
                    }
                    when (ex) {
                        is JuggleExceptionUser -> throw ex
                        is JuggleExceptionInternal -> throw ex
                        else ->
                            throw JuggleExceptionInternal("optimizer unknown ite: ${ex.message}")
                    }
                } catch (nsme: NoSuchMethodException) {
                    if (Constants.DEBUG_OPTIMIZE) {
                        println("nsme: ${nsme.message}")
                    }
                    throw JuggleExceptionInternal("optimizer nsme: ${nsme.message}")
                } catch (iae: IllegalAccessException) {
                    if (Constants.DEBUG_OPTIMIZE) {
                        println("iae: ${iae.message}")
                    }
                    throw JuggleExceptionInternal("optimizer iae: ${iae.message}")
                }
            }

            MenuCommand.FILE_SWAPHANDS -> {
                try {
                    val newpat = JMLPattern(view.pattern!!)
                    newpat.swapHands()
                    view.restartView(newpat, null)
                    view.addToUndoList(newpat)
                } catch (_: JuggleExceptionUser) {
                    throw JuggleExceptionInternal("Error in FILE_SWAPHANDS")
                }
            }

            MenuCommand.FILE_INVERTX -> {
                try {
                    val newpat = JMLPattern(view.pattern!!)
                    newpat.invertXAxis()
                    view.restartView(newpat, null)
                    view.addToUndoList(newpat)
                } catch (_: JuggleExceptionUser) {
                    throw JuggleExceptionInternal("Error in FILE_INVERTX")
                }
            }

            MenuCommand.FILE_INVERTTIME -> {
                try {
                    val newpat = JMLPattern(view.pattern!!)
                    newpat.invertTime()
                    view.restartView(newpat, null)
                    view.addToUndoList(newpat)
                } catch (_: JuggleExceptionUser) {
                    throw JuggleExceptionInternal("Error in FILE_INVERTTIME")
                }
            }

            MenuCommand.VIEW_RESTART -> view.restartView()

            MenuCommand.VIEW_ANIMPREFS -> {
                val jc = view.animationPrefs
                val japd = AnimationPrefsDialog(this)
                val newjc = japd.getPrefs(jc)

                if (newjc != jc) {
                    view.restartView(null, newjc)

                    if (newjc.width != jc.width || newjc.height != jc.height) {
                        if (this.isWindowMaximized) {
                            validate()
                        } else {
                            pack()
                        }
                    }
                }
            }

            MenuCommand.VIEW_UNDO -> view.undoEdit()
            MenuCommand.VIEW_REDO -> view.redoEdit()
            MenuCommand.VIEW_ZOOMIN -> if (view.zoomLevel < (MAX_ZOOM / ZOOM_PER_STEP)) {
                view.zoomLevel *= ZOOM_PER_STEP
            }

            MenuCommand.VIEW_ZOOMOUT -> if (view.zoomLevel > (MIN_ZOOM * ZOOM_PER_STEP)) {
                view.zoomLevel /= ZOOM_PER_STEP
            }

            MenuCommand.HELP_ABOUT -> ApplicationWindow.showAboutBox()
            MenuCommand.HELP_ONLINE -> ApplicationWindow.showOnlineHelp()
        }
    }

    private fun changeTitle() {
        val jd = JDialog(this, guistrings.getString("Change_title"), true)
        val gb = GridBagLayout()
        jd.contentPane.setLayout(gb)

        val tf = JTextField(20)
        tf.text = view.pattern!!.title

        val okbutton = JButton(guistrings.getString("OK"))
        okbutton.addActionListener { _: ActionEvent? ->
            val newpat = JMLPattern(view.pattern!!)
            newpat.title = tf.getText()
            view.restartView(newpat, null)
            view.addToUndoList(newpat)
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
        this.setTitle(view.pattern!!.title)
    }

    private fun changeTiming() {
        val jd = JDialog(this, guistrings.getString("Change_timing"), true)
        val gb = GridBagLayout()
        jd.contentPane.setLayout(gb)

        val p1 = JPanel()
        p1.setLayout(gb)
        val lab = JLabel(guistrings.getString("Rescale_percentage"))
        p1.add(lab)
        gb.setConstraints(
            lab, constraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )
        val tf = JTextField(7)
        tf.text = "100"
        p1.add(tf)
        gb.setConstraints(
            tf, constraints(GridBagConstraints.LINE_START, 1, 0, Insets(0, 5, 0, 0))
        )

        val okbutton = JButton(guistrings.getString("OK"))
        okbutton.addActionListener { _: ActionEvent? ->
            val scale: Double
            try {
                scale = jlParseFiniteDouble(tf.getText()) / 100.0
            } catch (_: NumberFormatException) {
                handleUserException(
                    this@PatternWindow,
                    "Number format error in rescale percentage"
                )
                return@addActionListener
            }
            if (scale > 0.0) {
                val newpat = JMLPattern(view.pattern!!)
                newpat.scaleTime(scale)
                view.restartView(newpat, null)
                view.addToUndoList(newpat)
            }
            jd.dispose()
        }

        jd.contentPane.add(p1)
        gb.setConstraints(
            p1, constraints(GridBagConstraints.LINE_START, 0, 0, Insets(10, 10, 0, 10))
        )
        jd.contentPane.add(okbutton)
        gb.setConstraints(
            okbutton,
            constraints(GridBagConstraints.LINE_END, 0, 1, Insets(10, 10, 10, 10))
        )
        jd.getRootPane().setDefaultButton(okbutton) // OK button is default
        jd.pack()
        jd.setLocationRelativeTo(this)
        jd.isVisible = true
    }

    //--------------------------------------------------------------------------
    // java.awt.Frame methods
    //--------------------------------------------------------------------------

    override fun setTitle(title: String?) {
        val newTitle = if (title == null || title.isEmpty()) {
            guistrings.getString("PWINDOW_Default_window_title")
        } else {
            title
        }
        super.setTitle(newTitle)
        ApplicationWindow.updateWindowMenus()
    }

    //--------------------------------------------------------------------------
    // java.awt.Window methods
    //--------------------------------------------------------------------------

    override fun dispose() {
        super.dispose()
        view.disposeView()
        SwingUtilities.invokeLater { ApplicationWindow.updateWindowMenus() }
    }

    companion object {
        private const val MAX_ZOOM: Double = 3.0
        private const val MIN_ZOOM: Double = 0.25
        private const val ZOOM_PER_STEP: Double = 1.1
        private var exitOnLastClose: Boolean = false

        // used for tiling the animation windows on the screen as they're created
        private const val NUM_TILES: Int = 8
        private val TILE_START: Point = Point(420, 50)
        private val TILE_OFFSET: Point = Point(25, 25)
        private var tileLocations: ArrayList<Point> = ArrayList()
        private var nextTileNum: Int = 0

        private var optimizer: Class<*>? = null
        private var optimizerLoaded: Boolean = false

        // Load the pattern optimizer. Do this using the reflection API so we can
        // omit the feature by leaving those source files out of the compile.

        private fun loadOptimizer() {
            if (optimizerLoaded) return
            try {
                optimizer = Class.forName("jugglinglab.optimizer.Optimizer")
                val optimizerAvailable: Method = optimizer!!.getMethod("optimizerAvailable")
                val canOptimize = optimizerAvailable.invoke(null) as Boolean
                if (!canOptimize) {
                    optimizer = null
                }
            } catch (e: Exception) {
                optimizer = null
                if (Constants.DEBUG_OPTIMIZE) {
                    println("Exception loading optimizer: $e")
                }
            }
            optimizerLoaded = true
        }

        // Return the location (screen pixels) of where the next animation window
        // should open.

        private val nextScreenLocation: Point
            get() {
                if (tileLocations.isEmpty()) {
                    val center = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint()
                    val locx = max(0, center.x - Constants.RESERVED_WIDTH_PIXELS / 2)
                    for (i in 0..<NUM_TILES) {
                        val locX: Int = locx + TILE_START.x + i * TILE_OFFSET.x
                        val locY: Int = TILE_START.y + i * TILE_OFFSET.y
                        tileLocations.add(Point(locX, locY))
                    }
                    nextTileNum = 0
                }
                val loc: Point = tileLocations[nextTileNum]
                if (++nextTileNum == NUM_TILES) {
                    nextTileNum = 0
                }
                return loc
            }

        // Check if a given pattern is already being animated, and if so then
        // bring that window to the front.
        //
        // Returns true if animation found, false if not.

        fun bringToFront(hash: Int): Boolean {
            for (fr in getFrames()) {
                if (fr is PatternWindow && fr.isVisible && fr.hashCode == hash) {
                    SwingUtilities.invokeLater { fr.toFront() }
                    return true
                }
            }
            return false
        }

        // Used when a single animation is created from the command line.

        fun setExitOnLastClose(value: Boolean) {
            exitOnLastClose = value
        }

        private val fileItems: List<String?> = listOf<String?>(
            "New Pattern",
            "New Pattern List",
            "Open JML...",
            "Save JML As...",
            "Save Animated GIF As...",
            null,
            "Duplicate",
            null,
            "Change Title...",
            "Change Overall Timing...",
            "Color Props",
            "Optimize",
            "Swap Hands",
            "Flip Pattern in X",
            "Flip Pattern in Time",
            null,
            "Close",
        )
        private val fileCommands: List<String?> = listOf(
            "newpat",
            "newpl",
            "open",
            "saveas",
            "savegifanim",
            null,
            "duplicate",
            null,
            "changetitle",
            "changetiming",
            "colorprops",
            "optimize",
            "swaphands",
            "invertx",
            "inverttime",
            null,
            "close",
        )
        private val fileShortcuts: CharArray = charArrayOf(
            'N',
            'L',
            'O',
            'S',
            'G',
            ' ',
            'D',
            ' ',
            ' ',
            ' ',
            ' ',
            'J',
            ' ',
            'M',
            'T',
            ' ',
            'W',
        )

        private val viewItems: List<String?> = listOf(
            "Simple",
            "Visual Editor",
            "Pattern Editor",
            "Selection Editor",
            null,
            "Undo",
            "Redo",
            null,
            "Restart",
            "Animation Preferences...",
            "Zoom In",
            "Zoom Out",
        )
        private val viewCommands: List<String?> = listOf(
            "simple",
            "visual_edit",
            "pattern_edit",
            "selection_edit",
            null,
            "undo",
            "redo",
            null,
            "restart",
            "prefs",
            "zoomin",
            "zoomout",
        )
        private val viewShortcuts: CharArray = charArrayOf(
            '1',
            '2',
            '3',
            '4',
            ' ',
            'Z',
            'Y',
            ' ',
            ' ',
            'P',
            '=',
            '-',
        )

        private val helpItems: List<String?> = listOf(
            "About Juggling Lab",
            "Juggling Lab Online Help",
        )
        private val helpCommands: List<String?> = listOf(
            "about",
            "online",
        )
    }
}
