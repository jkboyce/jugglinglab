//
// LadderDiagramPanel.kt
//
// This class draws the vertical ladder diagram on the right side of Edit view.
// This includes mouse interaction and editing functions.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.desktop

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.ui.common.*
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.path.Path
import org.jugglinglab.path.Path.Companion.newPath
import org.jugglinglab.prop.Prop
import org.jugglinglab.prop.Prop.Companion.newProp
import org.jugglinglab.jml.JmlProp
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.ParameterDescriptor
import org.jugglinglab.util.jlConstraints
import org.jugglinglab.util.jlGetImageResource
import org.jugglinglab.util.jlGetStringResource
import org.jugglinglab.util.jlHandleFatalException
import org.jugglinglab.util.jlHandleUserException
import org.jugglinglab.util.jlJfc
import org.jugglinglab.util.jlParseFiniteDouble
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import java.awt.*
import java.awt.event.*
import java.net.MalformedURLException
import java.util.Locale
import javax.swing.*
import javax.swing.border.BevelBorder
import javax.swing.event.CaretEvent
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.filechooser.FileNameExtensionFilter

class LadderDiagramPanel(
    val state: PatternAnimationState,
    val parentFrame: JFrame?
) : JPanel(), ActionListener {
    // ladder diagram panel
    private val composePanel = ComposePanel()

    // associated controller to handle mouse interactions
    private val controller = run {
        lateinit var ladderController: LadderDiagramController
        ladderController = LadderDiagramController(
            state = state,
            onMakePopup = { item, x, y ->
                val density = ladderController.currentDensity
                makePopupMenu(item).show(
                    composePanel,
                    (x / density).toInt(),
                    (y / density).toInt()
                )
            }
        )
        ladderController
    }

    // controls and parameters for JDialog shown for certain ladder diagram
    // interactions
    private var dialogControls: MutableList<JComponent>? = null
    private var dialogPd: List<ParameterDescriptor> = emptyList()

    init {
        layout = BorderLayout()
        add(composePanel, BorderLayout.CENTER)

        composePanel.setContent {
            val textMeasurer = rememberTextMeasurer()
            setPanelDimensions(
                LadderDiagramLayout.getPreferredWidthDp(
                    jugglers = state.pattern.numberOfJugglers,
                    paths = state.pattern.numberOfPaths,
                    textMeasurer = textMeasurer
                )
            )
            val colorScheme = lightColorScheme(
                background = Color.White,
                surface = Color.White
            )

            var zoom by remember { mutableFloatStateOf(1f) }
            val scrollState = rememberScrollState()

            LadderDiagramView(
                state = state,
                colorScheme = colorScheme,
                onPress = controller::handlePress,
                onDrag = controller::handleDrag,
                onRelease = controller::handleRelease,
                onLayoutUpdate = controller::onLayoutUpdate,
                textMeasurer = textMeasurer,
                zoom = zoom,
                onZoomChange = { zoom = it },
                scrollState = scrollState
            )
        }
    }

    //--------------------------------------------------------------------------
    // Initialization
    //--------------------------------------------------------------------------

    // Set the panel dimensions for layout.

    private fun setPanelDimensions(prefWidth: Int) {
        val jugglers = state.pattern.numberOfJugglers
        if (jugglers > LadderDiagramLayout.MAX_JUGGLERS) {
            preferredSize = Dimension(prefWidth, 1)
            minimumSize = Dimension(prefWidth, 1)
        } else {
            preferredSize = Dimension(prefWidth, 1)
            val minWidth: Int = LadderDiagramLayout.LADDER_MIN_WIDTH_PER_JUGGLER_DP * jugglers
            minimumSize = Dimension(minWidth, 1)
        }
    }

    //--------------------------------------------------------------------------
    // Popup menu creation and handling
    //--------------------------------------------------------------------------

    private fun makePopupMenu(laditem: LadderItem?): JPopupMenu {
        val popup = JPopupMenu()

        for ((i, popupResource) in LadderDiagramController.popupItemsStringResources.withIndex()) {
            if (popupResource == null) {
                popup.addSeparator()
                continue
            }

            val item =
                JMenuItem(jlGetStringResource(LadderDiagramController.popupItemsStringResources[i]!!))
            val command: String? = LadderDiagramController.popupCommands[i]
            item.actionCommand = command
            item.addActionListener(this)
            item.setEnabled(controller.isCommandEnabled(laditem, command))
            popup.add(item)
        }

        popup.setBorder(BevelBorder(BevelBorder.RAISED))

        popup.addPopupMenuListener(
            object : PopupMenuListener {
                override fun popupMenuCanceled(e: PopupMenuEvent?) {
                    controller.finishPopup()
                }

                override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {}
                override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {}
            })

        return popup
    }

    //--------------------------------------------------------------------------
    // java.awt.event.ActionListener methods
    //--------------------------------------------------------------------------

    override fun actionPerformed(event: ActionEvent) {
        try {
            val command = event.getActionCommand() ?: return
            when (command) {
                "defineprop" -> defineProp()
                "definethrow" -> defineThrow()
                else -> controller.performCommand(command)
            }
            controller.finishPopup()
        } catch (e: Exception) {
            jlHandleFatalException(JuggleExceptionInternal(e, state.pattern))
        }
    }

    @Throws(JuggleExceptionInternal::class)
    private fun defineProp() {
        // figure out which path number the user selected
        val pn: Int
        val popupItem = controller.popupItem
        if (popupItem is LadderEventItem) {
            if (popupItem.type != LadderItem.TYPE_TRANSITION) {
                throw JuggleExceptionInternal("defineProp() bad LadderItem type")
            }
            val ev = popupItem.event
            val transnum = popupItem.transNum
            val tr = ev.transitions[transnum]
            pn = tr.path
        } else {
            pn = (popupItem as LadderPathItem).pathNum
        }

        val animpropnum = state.propForPath
        val propnum = animpropnum[pn - 1]
        val startprop = state.pattern.getProp(propnum)
        val prtypes: List<String> = Prop.builtinProps

        val jd = JDialog(parentFrame, jlGetStringResource(Res.string.gui_define_prop), true)
        val gb = GridBagLayout()
        jd.contentPane.setLayout(gb)

        val p1 = JPanel()
        p1.setLayout(gb)
        val lab = JLabel(jlGetStringResource(Res.string.gui_prop_type))
        p1.add(lab)
        gb.setConstraints(
            lab, jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )

        val p2 = JPanel()
        p2.setLayout(gb)

        val cb1 = JComboBox(prtypes.toTypedArray())
        p1.add(cb1)
        gb.setConstraints(
            cb1, jlConstraints(GridBagConstraints.LINE_START, 1, 0, Insets(0, 10, 0, 0))
        )
        cb1.addActionListener { _: ActionEvent? ->
            val type = cb1.getItemAt(cb1.getSelectedIndex())
            try {
                val pt = if (type.equals(startprop.type, ignoreCase = true)) {
                    startprop
                } else {
                    newProp(type)
                }
                makeParametersPanel(p2, pt.parameterDescriptors)
            } catch (jeu: JuggleExceptionUser) {
                jlHandleUserException(jd, jeu.message)
                return@addActionListener
            }
            jd.pack()
        }
        val bp: List<String> = Prop.builtinProps
        for (i in bp.indices) {
            if (bp[i].equals(startprop.type, ignoreCase = true)) {
                cb1.setSelectedIndex(i)
                break
            }
        }

        val p3 = JPanel()
        p3.setLayout(gb)
        val cancelbutton = JButton(jlGetStringResource(Res.string.gui_cancel))
        p3.add(cancelbutton)
        gb.setConstraints(
            cancelbutton,
            jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )
        cancelbutton.addActionListener { _: ActionEvent? -> jd.dispose() }
        val okbutton = JButton(jlGetStringResource(Res.string.gui_ok))
        p3.add(okbutton)
        gb.setConstraints(
            okbutton, jlConstraints(GridBagConstraints.LINE_END, 1, 0, Insets(0, 10, 0, 0))
        )
        okbutton.addActionListener { _: ActionEvent? ->
            val type = cb1.getItemAt(cb1.getSelectedIndex())
            val mod: String?

            try {
                // fail if prop definition is invalid, before we change the pattern
                mod = this.dialogParameterList
                JmlProp(type.lowercase(Locale.getDefault()), mod).prop.isColorable
            } catch (jeu: JuggleExceptionUser) {
                jlHandleUserException(parentFrame, jeu.message)
                return@addActionListener
            }


            controller.applyPropDefinition(pn, type, mod)
            jd.dispose()
        }

        jd.contentPane.add(p1)
        gb.setConstraints(
            p1, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(10, 10, 0, 10))
        )
        jd.contentPane.add(p2)
        gb.setConstraints(
            p2, jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 0, 0, 0))
        )
        jd.contentPane.add(p3)
        gb.setConstraints(
            p3, jlConstraints(GridBagConstraints.LINE_END, 0, 2, Insets(10, 10, 10, 10))
        )
        jd.getRootPane().setDefaultButton(okbutton) // OK button is default

        val loc = Locale.getDefault()
        jd.applyComponentOrientation(ComponentOrientation.getOrientation(loc))

        jd.pack()
        jd.setResizable(false)
        jd.setLocationRelativeTo(this)
        jd.isVisible = true  // blocks until dispose() above
        dialogControls = null
    }

    @Throws(JuggleExceptionInternal::class)
    private fun defineThrow() {
        val popupItem = controller.popupItem
        if (popupItem !is LadderEventItem) {
            throw JuggleExceptionInternal("defineThrow() class format")
        }
        val evPrimary = popupItem.primary
        val tr = evPrimary.transitions[popupItem.transNum]

        val pptypes: List<String> = Path.builtinPaths

        val jd = JDialog(parentFrame, jlGetStringResource(Res.string.gui_define_throw), true)
        val gb = GridBagLayout()
        jd.contentPane.setLayout(gb)

        val p1 = JPanel()
        p1.setLayout(gb)
        val lab = JLabel(jlGetStringResource(Res.string.gui_throw_type))
        p1.add(lab)
        gb.setConstraints(
            lab, jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )

        val p2 = JPanel()
        p2.setLayout(gb)

        val cb1 = JComboBox(pptypes.toTypedArray())
        p1.add(cb1)
        gb.setConstraints(
            cb1, jlConstraints(GridBagConstraints.LINE_START, 1, 0, Insets(0, 10, 0, 0))
        )
        cb1.addActionListener { _: ActionEvent? ->
            val type = cb1.getItemAt(cb1.getSelectedIndex())
            try {
                val ppt = newPath(type)
                if (type.equals(tr.throwType, ignoreCase = true)) {
                    // populate with current throw parameters
                    ppt.initPath(tr.throwMod)
                }
                makeParametersPanel(p2, ppt.parameterDescriptors)
            } catch (jeu: JuggleExceptionUser) {
                jlHandleUserException(jd, jeu.message)
                return@addActionListener
            }
            jd.pack()
        }
        val bpp: List<String> = Path.builtinPaths
        for (i in bpp.indices) {
            if (bpp[i].equals(tr.throwType, ignoreCase = true)) {
                cb1.setSelectedIndex(i)
                break
            }
        }

        val p3 = JPanel()
        p3.setLayout(gb)
        val cancelbutton = JButton(jlGetStringResource(Res.string.gui_cancel))
        p3.add(cancelbutton)
        gb.setConstraints(
            cancelbutton,
            jlConstraints(GridBagConstraints.LINE_END, 0, 0, Insets(0, 0, 0, 0))
        )
        cancelbutton.addActionListener { _: ActionEvent? -> jd.dispose() }
        val okbutton = JButton(jlGetStringResource(Res.string.gui_ok))
        p3.add(okbutton)
        gb.setConstraints(
            okbutton, jlConstraints(GridBagConstraints.LINE_END, 1, 0, Insets(0, 10, 0, 0))
        )
        okbutton.addActionListener { _: ActionEvent? ->
            val type = cb1.getItemAt(cb1.getSelectedIndex())
            val mod = try {
                this.dialogParameterList
            } catch (jeu: JuggleExceptionUser) {
                jlHandleUserException(parentFrame, jeu.message)
                return@addActionListener
            }

            controller.applyThrowDefinition(popupItem, type, mod)
            jd.dispose()
        }

        jd.contentPane.add(p1)
        gb.setConstraints(
            p1, jlConstraints(GridBagConstraints.LINE_START, 0, 0, Insets(10, 10, 0, 10))
        )
        jd.contentPane.add(p2)
        gb.setConstraints(
            p2, jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(0, 0, 0, 0))
        )
        jd.contentPane.add(p3)
        gb.setConstraints(
            p3, jlConstraints(GridBagConstraints.LINE_END, 0, 2, Insets(10, 10, 10, 10))
        )
        jd.getRootPane().setDefaultButton(okbutton) // OK button is default

        jd.pack()
        jd.setResizable(false)
        jd.setLocationRelativeTo(this)
        jd.isVisible = true  // blocks until dispose() above
        dialogControls = null
    }

    //--------------------------------------------------------------------------
    // JDialog helper for defineProp() and defineThrow()
    //--------------------------------------------------------------------------

    private fun makeParametersPanel(jp: JPanel, pd: List<ParameterDescriptor>) {
        jp.removeAll()
        dialogControls = mutableListOf()
        dialogPd = pd

        if (pd.isEmpty())
            return

        val pdp = JPanel()
        val gb = GridBagLayout()
        jp.setLayout(gb)
        pdp.setLayout(gb)

        for (i in pd.indices) {
            val lab = JLabel(pd[i].name)
            pdp.add(lab)
            gb.setConstraints(
                lab, jlConstraints(GridBagConstraints.LINE_START, 0, i, Insets(0, 0, 0, 0))
            )
            if (pd[i].type == ParameterDescriptor.TYPE_BOOLEAN) {
                // JComboBox jcb = new JComboBox(booleanList);
                val jcb = JCheckBox()
                pdp.add(jcb)
                gb.setConstraints(
                    jcb, jlConstraints(GridBagConstraints.LINE_START, 1, i, Insets(2, 5, 2, 0))
                )
                dialogControls!!.add(jcb)
                val def = (pd[i].value) as Boolean
                // jcb.setSelectedIndex(def ? 0 : 1);
                jcb.setSelected(def)
            } else if (pd[i].type == ParameterDescriptor.TYPE_FLOAT) {
                val tf = JTextField(7)
                pdp.add(tf)
                gb.setConstraints(
                    tf, jlConstraints(GridBagConstraints.LINE_START, 1, i, Insets(0, 5, 0, 0))
                )
                dialogControls!!.add(tf)
                val def = (pd[i].value) as Double?
                tf.text = def.toString()
            } else if (pd[i].type == ParameterDescriptor.TYPE_CHOICE) {
                val choices = pd[i].range!!.toTypedArray()
                val jcb = JComboBox(choices)
                jcb.setMaximumRowCount(15)
                pdp.add(jcb)
                gb.setConstraints(
                    jcb, jlConstraints(GridBagConstraints.LINE_START, 1, i, Insets(0, 5, 0, 0))
                )
                dialogControls!!.add(jcb)

                val `val` = (pd[i].value) as String?
                for (j in choices.indices) {
                    if (`val`.equals(choices[j], ignoreCase = true)) {
                        jcb.setSelectedIndex(j)
                        break
                    }
                }
            } else if (pd[i].type == ParameterDescriptor.TYPE_INT) {
                val tf = JTextField(4)
                pdp.add(tf)
                gb.setConstraints(
                    tf, jlConstraints(GridBagConstraints.LINE_START, 1, i, Insets(0, 5, 0, 0))
                )
                dialogControls!!.add(tf)
                val def = (pd[i].value) as Int?
                tf.text = def.toString()

                tf.addCaretListener { _: CaretEvent? -> }
            } else if (pd[i].type == ParameterDescriptor.TYPE_ICON) {
                val fileSource = pd[i].value as String
                val composeImage = jlGetImageResource(fileSource)

                val icon = ImageIcon(composeImage.toAwtImage(), fileSource)
                val maxHeight = 100f
                if (icon.iconHeight > maxHeight) {
                    val scaleFactor = maxHeight / icon.iconHeight
                    val height = (scaleFactor * icon.iconHeight).toInt()
                    val width = (scaleFactor * icon.iconWidth).toInt()
                    icon.setImage(
                        icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH)
                    )
                }
                val label = JLabel(icon)

                // Clicking on the icon launches a file chooser for getting a new image
                label.addMouseListener(
                    object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent?) {
                            jlJfc.setFileFilter(
                                FileNameExtensionFilter(
                                    "Image file",
                                    "jpg",
                                    "jpeg",
                                    "gif",
                                    "png"
                                )
                            )
                            val result = jlJfc.showOpenDialog(this@LadderDiagramPanel)
                            if (result != JFileChooser.APPROVE_OPTION) {
                                return
                            }

                            try {
                                // Rebuild the parameter panel
                                pd[0].value = jlJfc.selectedFile.toURI().toURL().toString()
                                makeParametersPanel(jp, pd)
                                ((jp.getTopLevelAncestor()) as JDialog).pack()
                            } catch (_: MalformedURLException) {
                                // this should never happen
                                jlHandleFatalException(
                                    JuggleExceptionUser(jlGetStringResource(Res.string.error_malformed_url))
                                )
                            }
                        }
                    })
                // Add the icon to the panel
                pdp.add(label)
                gb.setConstraints(
                    label,
                    jlConstraints(GridBagConstraints.LINE_START, 1, i, Insets(0, 5, 5, 0))
                )
                dialogControls!!.add(label)
            }
        }

        jp.add(pdp)
        gb.setConstraints(
            pdp, jlConstraints(GridBagConstraints.LINE_START, 0, 1, Insets(10, 10, 0, 10))
        )
    }

    @get:Throws(JuggleExceptionUser::class)
    private val dialogParameterList: String?
        get() {
            var result: String? = null
            val dialog = dialogPd
            for (i in dialog.indices) {
                var term: String? = null
                val control: Any = dialogControls!![i]
                if (dialog[i].type == ParameterDescriptor.TYPE_BOOLEAN) {
                    // JComboBox jcb = (JComboBox)control;
                    // boolean val = ((jcb.getSelectedIndex() == 0) ? true : false);
                    val jcb = control as JCheckBox
                    val value = jcb.isSelected
                    val defValue = (dialog[i].defaultValue) as Boolean
                    if (value != defValue) {
                        term = value.toString()
                    }
                } else if (dialog[i].type == ParameterDescriptor.TYPE_FLOAT) {
                    val tf = control as JTextField
                    try {
                        val value = jlParseFiniteDouble(tf.getText())
                        val defValue = (dialog[i].defaultValue) as Double
                        if (value != defValue) {
                            term = tf.getText().trim { it <= ' ' }
                        }
                    } catch (_: NumberFormatException) {
                        val message =
                            jlGetStringResource(Res.string.error_number_format, dialog[i].name)
                        throw JuggleExceptionUser(message)
                    }
                } else if (dialog[i].type == ParameterDescriptor.TYPE_CHOICE) {
                    val jcb = control as JComboBox<*>
                    val ind = jcb.getSelectedIndex()
                    val value = dialog[i].range!![ind]
                    val defValue = (dialog[i].defaultValue) as String?
                    if (dialog[i].name == "color" && value == "custom") {
                        term = dialog[i].customData as String?
                    } else if (!value.equals(defValue, ignoreCase = true)) {
                        term = value
                    }
                } else if (dialog[i].type == ParameterDescriptor.TYPE_INT) {
                    val tf = control as JTextField
                    try {
                        val value = tf.getText().toInt()
                        val defValue = (dialog[i].defaultValue) as Int
                        if (value != defValue) {
                            term = tf.getText().trim { it <= ' ' }
                        }
                    } catch (_: NumberFormatException) {
                        val message =
                            jlGetStringResource(Res.string.error_number_format, dialog[i].name)
                        throw JuggleExceptionUser(message)
                    }
                } else if (dialog[i].type == ParameterDescriptor.TYPE_ICON) {
                    val label = control as JLabel
                    val icon = label.icon as ImageIcon
                    val def: String = dialog[i].defaultValue.toString()
                    if (icon.getDescription() != def) {
                        term = icon.getDescription()  // contains the URL string
                    }
                }

                if (term != null) {
                    term = "${dialog[i].name}=$term"
                    result = if (result == null) term else "$result;$term"
                }
            }
            return result
        }
}
