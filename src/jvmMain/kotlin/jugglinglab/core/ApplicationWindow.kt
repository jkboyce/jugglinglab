//
// ApplicationWindow.kt
//
// This is the main application window visible when Juggling Lab is launched
// as an application. The contents of the window are split into a different
// class (ApplicationPanel).
//
// Currently only a single notation (siteswap) is included with Juggling Lab
// so the notation menu is suppressed.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.core

import jugglinglab.generated.resources.*
import jugglinglab.core.PatternWindow.Companion.bringToFront
import jugglinglab.jml.JMLParser
import jugglinglab.jml.JMLPattern
import jugglinglab.jml.JMLPattern.Companion.fromBasePattern
import jugglinglab.notation.Pattern
import jugglinglab.util.*
import jugglinglab.util.ErrorDialog.handleFatalException
import jugglinglab.util.ErrorDialog.handleUserException
import jugglinglab.util.OpenFilesServer.cleanup
import jugglinglab.util.OpenFilesServer.startOpenFilesServer
import org.jetbrains.compose.resources.StringResource
import java.awt.*
import java.awt.desktop.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URI
import java.util.*
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.max
import kotlin.system.exitProcess

class ApplicationWindow(title: String?) : JFrame(title), ActionListener {
    var windowMenu: JMenu? = null
        private set

    init {
        createMenus()
        createContents()

        val center = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint()
        val locx = max(0, center.x - Constants.RESERVED_WIDTH_PIXELS / 2)
        setLocation(locx, 50)
        setResizable(false)
        isVisible = true

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE)
        addWindowListener(
            object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    try {
                        doMenuCommand(MenuCommand.FILE_EXIT)
                    } catch (_: Exception) {
                        exitProcess(0)
                    }
                }
            })

        // There are two ways we can handle requests from the OS to open files:
        // with a OpenFilesHandler (macOS) and with our own OpenFilesServer (Windows)
        if (!registerOpenFilesHandler()) {
            startOpenFilesServer()
        }

        // launch a background thread to check for updates online
        UpdateChecker()

        SwingUtilities.invokeLater { updateWindowMenus() }
    }

    //--------------------------------------------------------------------------
    // Create window contents
    //--------------------------------------------------------------------------

    private fun createContents() {
        val ap = ApplicationPanel(this)
        ap.isDoubleBuffered = true
        contentPane = ap // entire contents of window

        // does the real work of adding controls etc.
        ap.setNotation(Pattern.NOTATION_SITESWAP)

        val loc = Locale.getDefault()
        applyComponentOrientation(ComponentOrientation.getOrientation(loc))

        setBackground(Color(0.9f, 0.9f, 0.9f))
        pack()
    }

    //--------------------------------------------------------------------------
    // Menu creation and handlers
    //--------------------------------------------------------------------------

    private fun createMenus() {
        val mb = JMenuBar()
        mb.add(createFileMenu())

        if (Pattern.builtinNotations.size > 1) {
            val notationmenu = createNotationMenu()
            mb.add(notationmenu)
            // make siteswap notation the default selection
            notationmenu.getItem(Pattern.NOTATION_SITESWAP - 1).setSelected(true)
        }

        this.windowMenu = JMenu(getStringResource(Res.string.gui_window))
        mb.add(this.windowMenu)
        mb.add(createHelpMenu())
        jMenuBar = mb
    }

    private fun createFileMenu(): JMenu {
        val quitHandler =
            Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER)

        if (quitHandler) {
            Desktop.getDesktop()
                .setQuitHandler { _: QuitEvent?, response: QuitResponse? ->
                    try {
                        doMenuCommand(MenuCommand.FILE_EXIT)
                    } catch (_: JuggleExceptionInternal) {
                        response!!.performQuit()
                    }
                }
        }

        val filemenu = JMenu(getStringResource(Res.string.gui_file))

        for (i in 0..<(if (quitHandler) fileItems.size - 2 else fileItems.size)) {
            if (fileItems[i] == null) {
                filemenu.addSeparator()
                continue
            }

            val fileitem = JMenuItem(getStringResource(fileItemsRes[i]!!))
            if (fileShortcuts[i] != ' ') {
                fileitem.setAccelerator(
                    KeyStroke.getKeyStroke(
                        fileShortcuts[i].code, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
                    )
                )
            }
            fileitem.actionCommand = fileCommands[i]
            fileitem.addActionListener(this)
            filemenu.add(fileitem)
        }
        return filemenu
    }

    private fun createNotationMenu(): JMenu {
        val notationmenu = JMenu(getStringResource(Res.string.gui_notation))
        val buttonGroup = ButtonGroup()

        for (i in Pattern.builtinNotations.indices) {
            val notationitem = JRadioButtonMenuItem(Pattern.builtinNotations[i])
            notationitem.actionCommand = "notation${i + 1}"
            notationitem.addActionListener(this)
            notationmenu.add(notationitem)
            buttonGroup.add(notationitem)
        }

        return notationmenu
    }

    private fun createHelpMenu(): JMenu {
        // skip the about menu item if About handler was already installed
        // in JugglingLab.java
        val includeAbout =
            !Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.APP_ABOUT)

        val helpmenu = JMenu(getStringResource(Res.string.gui_help))

        for (i in (if (includeAbout) 0 else 1)..<helpItems.size) {
            if (helpItems[i] == null) {
                helpmenu.addSeparator()
            } else {
                val helpitem = JMenuItem(getStringResource(helpItemsRes[i]!!))
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
                "exit" -> doMenuCommand(MenuCommand.FILE_EXIT)
                "about" -> doMenuCommand(MenuCommand.HELP_ABOUT)
                "online" -> doMenuCommand(MenuCommand.HELP_ONLINE)
            }
        } catch (jei: JuggleExceptionInternal) {
            handleFatalException(jei)
        }
    }

    private enum class MenuCommand {
        FILE_NONE,
        FILE_NEWPAT,
        FILE_NEWPL,
        FILE_OPEN,
        FILE_EXIT,
        HELP_ABOUT,
        HELP_ONLINE,
    }

    @Suppress("KotlinConstantConditions")
    @Throws(JuggleExceptionInternal::class)
    private fun doMenuCommand(action: MenuCommand) {
        when (action) {
            MenuCommand.FILE_NONE -> {}
            MenuCommand.FILE_NEWPAT -> newPattern()
            MenuCommand.FILE_NEWPL -> (PatternListWindow("")).setTitle(null)
            MenuCommand.FILE_OPEN -> openJMLFile()
            MenuCommand.FILE_EXIT -> {
                val noOpenFilesHandler =
                    (!Desktop.isDesktopSupported()
                            || !Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE))

                if (noOpenFilesHandler) {
                    if (Constants.DEBUG_OPEN_SERVER) {
                        println("cleaning up server")
                    }
                    cleanup()
                }
                exitProcess(0)
            }

            MenuCommand.HELP_ABOUT -> showAboutBox()
            MenuCommand.HELP_ONLINE -> showOnlineHelp()
        }
    }

    companion object {
        // Try to register a handler for when the OS wants us to open a file type
        // associated with Juggling Lab (i.e., a .jml file)
        //
        // Returns true if successfully installed, false otherwise.

        private fun registerOpenFilesHandler(): Boolean {
            if (!Desktop.isDesktopSupported()) {
                return false
            }
            if (!Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE)) {
                return false
            }

            Desktop.getDesktop()
                .setOpenFileHandler { ofe: OpenFilesEvent? ->
                    if (Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)
                    ) {
                        Desktop.getDesktop().requestForeground(true)
                    }
                    try {
                        for (file in ofe!!.getFiles()) {
                            try {
                                openJMLFile(file)
                            } catch (jeu: JuggleExceptionUser) {
                                val message = getStringResource(Res.string.error_reading_file, file.getName())
                                handleUserException(null, message + ":\n" + jeu.message)
                            }
                        }
                    } catch (jei: JuggleExceptionInternal) {
                        handleFatalException(jei)
                    }
                }
            return true
        }

        // Update the "Window" menu attached to most of our JFrames.
        //
        // Call this whenever a window is added, removed, or retitled.

        fun updateWindowMenus() {
            val apps = ArrayList<ApplicationWindow>()
            val pls = ArrayList<PatternListWindow>()
            val anims = ArrayList<PatternWindow>()
            val menus = ArrayList<JMenu>()

            for (fr in getFrames()) {
                if (!fr.isVisible) continue
                when (fr) {
                    is ApplicationWindow -> {
                        apps.add(fr)
                        menus.add(fr.windowMenu!!)
                    }
                    is PatternListWindow -> {
                        pls.add(fr)
                        menus.add(fr.windowMenu!!)
                    }
                    is PatternWindow -> {
                        anims.add(fr)
                        menus.add(fr.windowMenu)
                    }
                }
            }

            val al =
                ActionListener { ae: ActionEvent? ->
                    val command = ae!!.getActionCommand()
                    if (command == "front") {
                        val foregroundSupported =
                            Desktop.isDesktopSupported()
                                    && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)

                        if (foregroundSupported) {
                            Desktop.getDesktop().requestForeground(true)
                        } else {
                            for (fr in apps) {
                                fr.toFront()
                            }
                            for (fr in pls) {
                                fr.toFront()
                            }
                            for (fr in anims) {
                                fr.toFront()
                            }
                        }
                        return@ActionListener
                    } else {
                        var windownum = command.toInt()

                        if (windownum < apps.size) {
                            apps[windownum].toFront()
                            return@ActionListener
                        }
                        windownum -= apps.size
                        if (windownum < pls.size) {
                            pls[windownum].toFront()
                            return@ActionListener
                        }
                        windownum -= pls.size
                        if (windownum < anims.size) {
                            anims[windownum].toFront()
                            return@ActionListener
                        }

                        handleFatalException(
                            JuggleExceptionInternal("Window number out of range: $command")
                        )
                    }
                }

            for (wm in menus) {
                wm.removeAll()

                val alltofront = JMenuItem(getStringResource(Res.string.gui_bring_all_to_front))
                alltofront.actionCommand = "front"
                alltofront.addActionListener(al)
                wm.add(alltofront)

                wm.addSeparator()

                var windownum = 0
                for (aw in apps) {
                    val awitem = JMenuItem(aw.getTitle())
                    awitem.actionCommand = (windownum++).toString()
                    awitem.addActionListener(al)
                    wm.add(awitem)
                }

                // if (apps.size() > 0)
                //    wm.addSeparator();
                for (pl in pls) {
                    val plitem = JMenuItem(pl.getTitle())
                    plitem.actionCommand = (windownum++).toString()
                    plitem.addActionListener(al)
                    wm.add(plitem)
                }

                // if (pls.size() > 0)
                //    wm.addSeparator();
                for (anim in anims) {
                    val animitem = JMenuItem(anim.getTitle())
                    animitem.actionCommand = (windownum++).toString()
                    animitem.addActionListener(al)
                    wm.add(animitem)
                }
            }
        }

        // Do the File menu "New Pattern" command.

        @Throws(JuggleExceptionInternal::class)
        fun newPattern() {
            try {
                val pat = fromBasePattern("Siteswap", "pattern=3")
                val pw = PatternWindow("3", pat, AnimationPrefs())
                pw.viewMode = AnimationPrefs.VIEW_PATTERN
            } catch (jeu: JuggleExceptionUser) {
                throw JuggleExceptionInternal(jeu.message)
            }
        }

        // Show the user a file chooser to open a JML file.

        @Throws(JuggleExceptionInternal::class)
        fun openJMLFile() {
            jfc.setFileFilter(FileNameExtensionFilter("JML file", "jml"))
            if (jfc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
                return
            }

            val file = jfc.selectedFile
            if (file != null) {
                try {
                    openJMLFile(file)
                } catch (jeu: JuggleExceptionUser) {
                    val message = getStringResource(Res.string.error_reading_file, file.getName())
                    handleUserException(null, message + ":\n" + jeu.message)
                }
            }
        }

        // Open a JML file.

        @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
        fun openJMLFile(jmlf: File) {
            try {
                val parser = JMLParser()
                parser.parse(jmlf.readText())

                when (parser.fileType) {
                    JMLParser.JML_PATTERN -> {
                        val pat = JMLPattern(parser.tree!!)
                        pat.layoutPattern()
                        if (!bringToFront(pat.hashCode)) {
                            val pw = PatternWindow(pat.title, pat, AnimationPrefs())
                            pw.setJMLFilename(jmlf.getName())
                        }
                    }

                    JMLParser.JML_LIST -> {
                        val plw = PatternListWindow(parser.tree)
                        plw.setJmlFilename(jmlf.getName())
                    }

                    else -> {
                        val message = getStringResource(Res.string.error_invalid_jml)
                        throw JuggleExceptionUser(message)
                    }
                }
            } catch (fnfe: FileNotFoundException) {
                val message = getStringResource(Res.string.error_file_not_found)
                throw JuggleExceptionUser("$message: ${fnfe.message}")
            } catch (ioe: IOException) {
                val message = getStringResource(Res.string.error_io)
                throw JuggleExceptionUser("$message: ${ioe.message}")
            }
        }

        // Show the user the "About" dialog box.

        @Suppress("AssignedValueIsNeverRead")
        fun showAboutBox() {
            val aboutBox = JFrame(getStringResource(Res.string.gui_about_juggling_lab))
            aboutBox.setDefaultCloseOperation(DISPOSE_ON_CLOSE)

            val aboutPanel = JPanel(BorderLayout())
            aboutPanel.setOpaque(true)

            val url = ApplicationWindow::class.java.getResource("/about.png")
            if (url != null) {
                val aboutPicture = ImageIcon(url, "A lab")
                val aboutLabel = JLabel(aboutPicture)
                aboutPanel.add(aboutLabel, BorderLayout.LINE_START)
            }

            val textPanel = JPanel()
            aboutPanel.add(textPanel, BorderLayout.LINE_END)
            val gb = GridBagLayout()
            textPanel.setLayout(gb)

            val abouttext1 = JLabel("Juggling Lab")
            abouttext1.setFont(Font("SansSerif", Font.BOLD, 18))
            textPanel.add(abouttext1)
            gb.setConstraints(
                abouttext1,
                constraints(GridBagConstraints.LINE_START, 0, 0, Insets(15, 15, 0, 15))
            )

            val message5 = getStringResource(Res.string.gui_version, Constants.VERSION)
            val abouttext5 = JLabel(message5)
            abouttext5.setFont(Font("SansSerif", Font.PLAIN, 16))
            textPanel.add(abouttext5)
            gb.setConstraints(
                abouttext5,
                constraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 15, 0, 15))
            )

            val message6 = getStringResource(Res.string.gui_copyright_message, Constants.YEAR)
            val abouttext6 = JLabel(message6)
            abouttext6.setFont(Font("SansSerif", Font.PLAIN, 14))
            textPanel.add(abouttext6)
            gb.setConstraints(
                abouttext6,
                constraints(GridBagConstraints.LINE_START, 0, 2, Insets(15, 15, 15, 15))
            )

            val abouttext3 = JLabel(getStringResource(Res.string.gui_gpl_message))
            abouttext3.setFont(Font("SansSerif", Font.PLAIN, 12))
            textPanel.add(abouttext3)
            gb.setConstraints(
                abouttext3,
                constraints(GridBagConstraints.LINE_START, 0, 3, Insets(0, 15, 15, 15))
            )

            val javaversion = System.getProperty("java.version")
            val javavmname = System.getProperty("java.vm.name")
            val javavmversion = System.getProperty("java.vm.version")

            var gridrow = 4
            if (javaversion != null) {
                val java1 = JLabel("Java version $javaversion")
                java1.setFont(Font("SansSerif", Font.PLAIN, 12))
                textPanel.add(java1)
                gb.setConstraints(
                    java1,
                    constraints(
                        GridBagConstraints.LINE_START, 0, gridrow++, Insets(0, 15, 0, 15)
                    )
                )
            }
            if (javavmname != null && javavmversion != null) {
                val java2 = JLabel("$javavmname ($javavmversion)")
                java2.setFont(Font("SansSerif", Font.PLAIN, 12))
                textPanel.add(java2)
                gb.setConstraints(
                    java2,
                    constraints(
                        GridBagConstraints.LINE_START, 0, gridrow++, Insets(0, 15, 0, 15)
                    )
                )
            }

            val okbutton = JButton(getStringResource(Res.string.gui_ok))
            textPanel.add(okbutton)
            gb.setConstraints(
                okbutton,
                constraints(GridBagConstraints.LINE_END, 0, gridrow++, Insets(15, 15, 15, 15))
            )
            okbutton.addActionListener { _: ActionEvent? ->
                aboutBox.isVisible = false
                aboutBox.dispose()
            }

            aboutBox.contentPane = aboutPanel

            val loc = Locale.getDefault()
            aboutBox.applyComponentOrientation(ComponentOrientation.getOrientation(loc))

            aboutBox.pack()
            aboutBox.setResizable(false)
            aboutBox.setLocationRelativeTo(null) // center frame on screen
            aboutBox.isVisible = true
        }

        // Bring the user to the online help page.

        fun showOnlineHelp() {
            val browseSupported =
                (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            var browseProblem = false

            if (browseSupported) {
                try {
                    Desktop.getDesktop().browse(URI(Constants.HELP_URL))
                } catch (_: Exception) {
                    browseProblem = true
                }
            }

            if (!browseSupported || browseProblem) {
                LabelDialog(null, "Help", "Find online help at " + Constants.HELP_URL)
            }
        }

        private val fileItems: List<String?> = listOf(
            "New Pattern",
            "New Pattern List",
            "Open JML...",
            null,
            "Quit",
        )
        private val fileItemsRes: List<StringResource?> = listOf(
            Res.string.gui_new_pattern,
            Res.string.gui_new_pattern_list,
            Res.string.gui_open_jml___,
            null,
            Res.string.gui_quit,
        )
        private val fileCommands: List<String?> = listOf(
            "newpat",
            "newpl",
            "open",
            null,
            "exit",
        )
        private val fileShortcuts: CharArray = charArrayOf(
            'N',
            'L',
            'O',
            ' ',
            'Q',
        )

        private val helpItems: List<String?> = listOf(
            "About Juggling Lab",
            "Juggling Lab Online Help",
        )
        private val helpItemsRes: List<StringResource?> = listOf(
            Res.string.gui_about_juggling_lab,
            Res.string.gui_juggling_lab_online_help,
        )
        private val helpCommands: List<String?> = listOf(
            "about",
            "online",
        )
    }
}
