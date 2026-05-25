//
// PatternListDialogs.kt
//
// Compose dialogs for the pattern list view.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.ui.components.JlInputDialog
import org.jugglinglab.ui.components.JlConfirmDialog
import androidx.compose.runtime.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun PatternListDialogs(
    activeDialog: PatternListDialog?,
    onDismissRequest: () -> Unit,
    onRenameList: (String) -> Unit,
    onSaveAs: (String) -> Unit,
    onDeleteList: () -> Unit,
    onInsertText: (Int, String) -> Unit,
    onChangeDisplayText: (Int, String) -> Unit
) {
    when (activeDialog) {
        is PatternListDialog.RenameList -> {
            JlInputDialog(
                title = stringResource(Res.string.gui_mobile_rename_title),
                initialText = activeDialog.currentFilename,
                onConfirm = onRenameList,
                onDismissRequest = onDismissRequest
            )
        }

        is PatternListDialog.SaveAs -> {
            JlInputDialog(
                title = stringResource(Res.string.gui_mobile_save_as),
                initialText = activeDialog.defaultFilename,
                onConfirm = onSaveAs,
                onDismissRequest = onDismissRequest
            )
        }

        is PatternListDialog.DeleteList -> {
            JlConfirmDialog(
                title = stringResource(Res.string.gui_mobile_delete),
                text = "Delete file \"${activeDialog.filename}\"?",
                onConfirm = onDeleteList,
                onDismissRequest = onDismissRequest
            )
        }

        is PatternListDialog.InsertText -> {
            JlInputDialog(
                title = stringResource(Res.string.gui_mobile_plpopup_insert_text_title),
                initialText = "",
                onConfirm = { text -> onInsertText(activeDialog.index, text) },
                onDismissRequest = onDismissRequest
            )
        }

        is PatternListDialog.ChangeDisplayText -> {
            JlInputDialog(
                title = stringResource(Res.string.gui_mobile_plpopup_change_display_text_title),
                initialText = activeDialog.currentText,
                onConfirm = { text -> onChangeDisplayText(activeDialog.index, text) },
                onDismissRequest = onDismissRequest
            )
        }

        null -> {}
    }
}

sealed class PatternListDialog {
    data class RenameList(val currentFilename: String) : PatternListDialog()
    data class SaveAs(val defaultFilename: String) : PatternListDialog()
    data class DeleteList(val filename: String) : PatternListDialog()
    data class InsertText(val index: Int) : PatternListDialog()
    data class ChangeDisplayText(val index: Int, val currentText: String) : PatternListDialog()
}
