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
import jugglinglab.composeapp.generated.resources.*
import jugglinglab.jml.JMLPattern
import jugglinglab.jml.PatternBuilder
import jugglinglab.prop.Prop
import jugglinglab.prop.Prop.Companion.colorStringResources
import jugglinglab.util.*
import jugglinglab.util.jlHandleFatalException
import jugglinglab.util.jlHandleUserException
import jugglinglab.view.*
import org.jetbrains.compose.resources.StringResource
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

    private var lastJmlFilename: String? = null

    init {
        createMenus()
        createInitialView(pat, jc)
        updateUndoMenu()

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
                jlHandleFatalException(je)
            }
        }

        SwingUtilities.invokeLater { ApplicationWindow.updateWindowMenus() }
    }

    // Create a new PatternWindow with the same JMLPattern and default View as
    // an existing PatternWindow.

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private constructor(pw: PatternWindow) : this(
        pw.getTitle(),
        pw.view.state.pattern,
        pw.view.state.prefs
    )

    //--------------------------------------------------------------------------
    // Methods to create and manage window contents
    //--------------------------------------------------------------------------

    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    private fun createInitialView(pattern: JMLPattern, prefs: AnimationPrefs?) {
        val jc = prefs ?: AnimationPrefs()
        val mode = when {
            (jc.defaultView != AnimationPrefs.VIEW_NONE) -> jc.defaultView
            (pattern.numberOfJugglers > LadderDiagram.MAX_JUGGLERS) ->
                AnimationPrefs.VIEW_SIMPLE

            else -> AnimationPrefs.VIEW_EDIT
        }
        viewMenu.getItem(mode - 1).setSelected(true)

        val state = PatternAnimationState(
            initialPattern = pattern,
            initialPrefs = jc
        )
        state.addListener(onNewPatternUndo = {
            updateUndoMenu()
        })
        when (mode) {
            AnimationPrefs.VIEW_NONE -> {}
            AnimationPrefs.VIEW_SIMPLE -> view = SimpleView(state, this)
            AnimationPrefs.VIEW_EDIT -> view = EditView(state, this)
            AnimationPrefs.VIEW_PATTERN -> view = PatternView(state, this)
            AnimationPrefs.VIEW_SELECTION -> view = SelectionView(state, this)
        }

        view.setOpaque(true)
        view.isDoubleBuffered = true
        contentPane = view

        val loc = Locale.getDefault()
        applyComponentOrientation(ComponentOrientation.getOrientation(loc))
        setBackground(Color.white)
        pack()

        view.restartView(pattern, jc)
    }

    @set:Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    var viewMode: Int
        get() = when (view) {
            is SimpleView -> AnimationPrefs.VIEW_SIMPLE
            is EditView -> AnimationPrefs.VIEW_EDIT
            is PatternView -> AnimationPrefs.VIEW_PATTERN
            is SelectionView -> AnimationPrefs.VIEW_SELECTION
            else -> AnimationPrefs.VIEW_NONE
        }
        set(mode) {
            // `mode` is one of the View.VIEW_X constants.
            viewMenu.getItem(mode - 1).setSelected(true)

            // move the state from the old view to the new
            val state = view.state.apply {
                removeAllListeners()
                update(selectedItemHashCode = 0)
                addListener(onNewPatternUndo = { updateUndoMenu() })
            }

            val newview: View = when (mode) {
                AnimationPrefs.VIEW_SIMPLE -> SimpleView(state, this)
                AnimationPrefs.VIEW_EDIT -> EditView(state, this)
                AnimationPrefs.VIEW_PATTERN -> PatternView(state, this)
                AnimationPrefs.VIEW_SELECTION -> SelectionView(state, this)
                else -> throw JuggleExceptionInternal("setViewMode: problem creating view")
            }.apply {
                setOpaque(true)
                isDoubleBuffered = true
            }

            contentPane = newview
            if (isWindowMaximized) validate() else pack()

            newview.restartView(state.pattern, state.prefs)
            view.disposeView()
            view = newview
        }

    val pattern: JMLPattern
        get() = view.state.pattern

    val animationPrefs: AnimationPrefs
        get() = view.state.prefs

    // Used for testing whether a given JMLPattern is already being animated.
    val jlHashCode: Int
        get() = view.state.pattern.jlHashCode

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
        windowMenu = JMenu(jlGetStringResource(Res.string.gui_window))
        mb.add(windowMenu)
        mb.add(createHelpMenu())
        jMenuBar = mb
    }

    private fun createFileMenu(): JMenu {
        val fileMenu = JMenu(jlGetStringResource(Res.string.gui_file))
        for (i in fileItems.indices) {
            if (fileItems[i] == null) {
                fileMenu.addSeparator()
                continue
            }

            if (fileCommands[i] == "colorprops") {
                colorsMenu = JMenu(jlGetStringResource(fileItemsStringResources[i]!!))
                colorsMenu.add(JMenuItem(jlGetStringResource(Res.string.gui_pcmenu_mixed)).apply {
                    actionCommand = "colors_mixed"
                    addActionListener(this@PatternWindow)
                })
                colorsMenu.add(JMenuItem(jlGetStringResource(Res.string.gui_pcmenu_orbits)).apply {
                    actionCommand = "colors_orbits"
                    addActionListener(this@PatternWindow)
                })
                colorsMenu.addSeparator()
                for (i in Prop.colorNames.indices) {
                    colorsMenu.add(JMenuItem(jlGetStringResource(colorStringResources[i])).apply {
                        actionCommand = "colors_${Prop.colorNames[i]}"
                        addActionListener(this@PatternWindow)
                    })
                }
                fileMenu.add(colorsMenu)
                continue
            }

            val fileItem = JMenuItem(jlGetStringResource(fileItemsStringResources[i]!!))
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

            if (fileCommands[i] == "optimize" && optimizerWrapper == null) {
                fileItem.setEnabled(false)
            }
        }
        return fileMenu
    }

    // Enable or disable the "Color Props" menu depending on whether the pattern
    // is colorable. Call this when the pattern changes.

    fun updateColorsMenu() {
        colorsMenu.isEnabled = view.state.pattern.isColorable
    }

    private fun createViewMenu(): JMenu {
        viewMenu = JMenu(jlGetStringResource(Res.string.gui_view))
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
                    JRadioButtonMenuItem(jlGetStringResource(viewItemsStringResources[i]!!))

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
                val viewitem = JMenuItem(jlGetStringResource(viewItemsStringResources[i]!!))

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
        val undoIndex = view.state.undoIndex
        val undoEnabled = (undoIndex > 0)
        val redoEnabled = (undoIndex < view.state.undoList.size - 1)

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

        var menuname: String = jlGetStringResource(Res.string.gui_help)
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
                    if (viewMode != AnimationPrefs.VIEW_SIMPLE) {
                        viewMode = AnimationPrefs.VIEW_SIMPLE
                    }
                }

                "visual_edit" -> {
                    if (viewMode != AnimationPrefs.VIEW_EDIT) {
                        viewMode = AnimationPrefs.VIEW_EDIT
                    }
                }

                "pattern_edit" -> {
                    if (viewMode != AnimationPrefs.VIEW_PATTERN) {
                        viewMode = AnimationPrefs.VIEW_PATTERN
                    }
                }

                "selection_edit" -> {
                    if (viewMode != AnimationPrefs.VIEW_SELECTION) {
                        viewMode = AnimationPrefs.VIEW_SELECTION
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
                        val newpat = view.state.pattern.withPropColors(colorString)
                        view.restartView(newpat, null, coldRestart = false)
                        view.state.addCurrentToUndoList()
                    } catch (_: JuggleExceptionUser) {
                        throw JuggleExceptionInternal("Error in FILE_PROPCOLORS")
                    }
                }
            }
        } catch (je: JuggleExceptionUser) {
            jlHandleUserException(this, je.message)
        } catch (jei: JuggleExceptionInternal) {
            jlHandleFatalException(jei)
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
                var fname = lastJmlFilename
                if (fname == null) {
                    fname = getTitle() + ".jml"  // default filename
                }
                fname = jlSanitizeFilename(fname)
                jlJfc.setSelectedFile(File(fname))
                jlJfc.setFileFilter(FileNameExtensionFilter("JML file", "jml"))
                if (jlJfc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                    return
                }

                var f = jlJfc.selectedFile ?: return
                if (!f.absolutePath.endsWith(".jml")) {
                    f = File(f.absolutePath + ".jml")
                }
                jlErrorIfNotSanitized(f.getName())
                lastJmlFilename = f.getName()
                try {
                    val fw = FileWriter(f)
                    view.state.pattern.writeJML(fw, writeTitle = true, writeInfo = true)
                    fw.close()
                } catch (fnfe: FileNotFoundException) {
                    throw JuggleExceptionInternal("FileNotFound: ${fnfe.message}")
                } catch (ioe: IOException) {
                    throw JuggleExceptionInternal("IOException: ${ioe.message}")
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
                jlJfc.setSelectedFile(File(fname))
                jlJfc.setFileFilter(FileNameExtensionFilter("GIF file", "gif"))
                if (jlJfc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                    return
                }

                var f = jlJfc.selectedFile ?: return
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
                newpw.title = "$title copy"
            }

            MenuCommand.FILE_TITLE -> changeTitle()
            MenuCommand.FILE_RESCALE -> changeTiming()
            MenuCommand.FILE_OPTIMIZE -> {
                if (Constants.DEBUG_OPTIMIZE) {
                    println("--- Optimizing in PatternWindow ---")
                }
                optimizerWrapper?.let {
                    try {
                        val newPat = it.optimize(view.state.pattern)
                        view.restartView(newPat, null, coldRestart = false)
                        view.state.addCurrentToUndoList()
                    } catch (ite: InvocationTargetException) {
                        // Unwrap the actual exception thrown by the optimizer
                        when (val cause = ite.cause) {
                            is JuggleExceptionUser -> throw cause
                            is JuggleExceptionInternal -> throw cause
                            else -> throw JuggleExceptionInternal("Optimizer failed: ${cause?.message}")
                        }
                    }
                }
            }

            MenuCommand.FILE_SWAPHANDS -> {
                try {
                    val newpat = view.state.pattern.withInvertedXAxis(flipXCoordinate = false)
                    view.restartView(newpat, null, coldRestart = false)
                    view.state.addCurrentToUndoList()
                } catch (e: JuggleExceptionUser) {
                    throw JuggleExceptionInternal("Error in FILE_SWAPHANDS: ${e.message}")
                }
            }

            MenuCommand.FILE_INVERTX -> {
                try {
                    val newpat = view.state.pattern.withInvertedXAxis()
                    view.restartView(newpat, null, coldRestart = false)
                    view.state.addCurrentToUndoList()
                } catch (e: JuggleExceptionUser) {
                    throw JuggleExceptionInternal("Error in FILE_INVERTX: ${e.message}")
                }
            }

            MenuCommand.FILE_INVERTTIME -> {
                try {
                    val newpat = view.state.pattern.withInvertedTime()
                    view.restartView(newpat, null, coldRestart = false)
                    view.state.addCurrentToUndoList()
                } catch (e: JuggleExceptionUser) {
                    throw JuggleExceptionInternal("Error in FILE_INVERTTIME: ${e.message}")
                }
            }

            MenuCommand.VIEW_RESTART -> view.restartView()

            MenuCommand.VIEW_ANIMPREFS -> {
                val jc = view.state.prefs
                val japd =
                    if (jlIsSwing()) AnimationPrefsDialogSwing(this) else AnimationPrefsDialog(this)
                val newjc = japd.getPrefs(jc)

                if (newjc != jc) {
                    view.restartView(null, newjc)

                    if (newjc.width != jc.width || newjc.height != jc.height) {
                        if (isWindowMaximized) {
                            validate()
                        } else {
                            pack()
                        }
                    }
                }
            }

            MenuCommand.VIEW_UNDO -> view.undoEdit()
            MenuCommand.VIEW_REDO -> view.redoEdit()
            MenuCommand.VIEW_ZOOMIN -> if (view.zoom < (MAX_ZOOM / ZOOM_PER_STEP)) {
                view.zoom *= ZOOM_PER_STEP
            }

            MenuCommand.VIEW_ZOOMOUT -> if (view.zoom > (MIN_ZOOM * ZOOM_PER_STEP)) {
                view.zoom /= ZOOM_PER_STEP
            }

            MenuCommand.HELP_ABOUT -> ApplicationWindow.showAboutBox()
            MenuCommand.HELP_ONLINE -> ApplicationWindow.showOnlineHelp()
        }
    }

    private fun changeTitle() {
        val jd = JDialog(this, jlGetStringResource(Res.string.gui_change_title), true)
        val gb = GridBagLayout()
        jd.contentPane.setLayout(gb)

        val tf = JTextField(20)
        tf.text = view.state.pattern.title

        val okbutton = JButton(jlGetStringResource(Res.string.gui_ok))
        okbutton.addActionListener { _: ActionEvent? ->
            val rec = PatternBuilder.fromJMLPattern(view.state.pattern)
            rec.setTitleString(tf.getText())
            val newpat = JMLPattern.fromPatternBuilder(rec)
            view.restartView(newpat, null)
            view.state.addCurrentToUndoList()
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
        this.setTitle(view.state.pattern.title)
    }

    private fun changeTiming() {
        val jd = JDialog(this, jlGetStringResource(Res.string.gui_change_timing), true)
        val gb = GridBagLayout()
        jd.contentPane.setLayout(gb)

        val p1 = JPanel()
        p1.setLayout(gb)
        val lab = JLabel(jlGetStringResource(Res.string.gui_rescale_percentage))
        p1.add(lab)
        gb.setConstraints(
            lab, jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )
        val tf = JTextField(7)
        tf.text = "100"
        p1.add(tf)
        gb.setConstraints(
            tf, jlConstraints(GridBagConstraints.LINE_START, 1, 0, Insets(0, 5, 0, 0))
        )

        val okbutton = JButton(jlGetStringResource(Res.string.gui_ok))
        okbutton.addActionListener { _: ActionEvent? ->
            val scale: Double
            try {
                scale = jlParseFiniteDouble(tf.getText()) / 100.0
            } catch (_: NumberFormatException) {
                jlHandleUserException(
                    this@PatternWindow,
                    "Number format error in rescale percentage"
                )
                return@addActionListener
            }
            if (scale > 0.0) {
                val newpat = view.state.pattern.withScaledTime(scale)
                view.restartView(newpat, null)
                view.state.addCurrentToUndoList()
            }
            jd.dispose()
        }

        jd.contentPane.add(p1)
        gb.setConstraints(
            p1, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(10, 10, 0, 10))
        )
        jd.contentPane.add(okbutton)
        gb.setConstraints(
            okbutton,
            jlConstraints(GridBagConstraints.LINE_END, 0, 1, Insets(10, 10, 10, 10))
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
        val newTitle = if (title.isNullOrEmpty()) {
            jlGetStringResource(Res.string.gui_pwindow_default_window_title)
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

        /** Defines the contract for the optional optimizer module. */
        private interface OptimizerWrapper {
            fun optimize(pat: JMLPattern): JMLPattern
        }

        // Lazily load the Optimizer class via reflection. If successful, it creates
        // a wrapper that implements the [OptimizerWrapper] interface. If not, it's null.

        private val optimizerWrapper: OptimizerWrapper? by lazy {
            runCatching {
                val optimizerClass = Class.forName("jugglinglab.optimizer.Optimizer")
                val optimizerAvailableMethod: Method =
                    optimizerClass.getMethod("optimizerAvailable")
                val canOptimize = optimizerAvailableMethod.invoke(null) as? Boolean ?: false

                if (canOptimize) {
                    val optimizeMethod: Method =
                        optimizerClass.getMethod("optimize", JMLPattern::class.java)
                    // Return an object that implements our wrapper interface
                    object : OptimizerWrapper {
                        override fun optimize(pat: JMLPattern): JMLPattern =
                            optimizeMethod.invoke(null, pat) as JMLPattern
                    }
                } else null
            }.getOrNull()
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
                if (fr is PatternWindow && fr.isVisible && fr.jlHashCode == hash) {
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

        private val fileItems: List<String?> = listOf(
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
        private val fileItemsStringResources: List<StringResource?> = listOf(
            Res.string.gui_new_pattern,
            Res.string.gui_new_pattern_list,
            Res.string.gui_open_jml___,
            Res.string.gui_save_jml_as___,
            Res.string.gui_save_animated_gif_as___,
            null,
            Res.string.gui_duplicate,
            null,
            Res.string.gui_change_title___,
            Res.string.gui_change_overall_timing___,
            Res.string.gui_color_props,
            Res.string.gui_optimize,
            Res.string.gui_swap_hands,
            Res.string.gui_flip_pattern_in_x,
            Res.string.gui_flip_pattern_in_time,
            null,
            Res.string.gui_close,
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
        private val viewItemsStringResources: List<StringResource?> = listOf(
            Res.string.gui_simple,
            Res.string.gui_visual_editor,
            Res.string.gui_pattern_editor,
            Res.string.gui_selection_editor,
            null,
            Res.string.gui_undo,
            Res.string.gui_redo,
            null,
            Res.string.gui_restart,
            Res.string.gui_animation_preferences___,
            Res.string.gui_zoom_in,
            Res.string.gui_zoom_out,
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
