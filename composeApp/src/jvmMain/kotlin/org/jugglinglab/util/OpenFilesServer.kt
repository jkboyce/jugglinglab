//
// OpenFilesServer.kt
//
// This is a utility class to handle open file messages when the user double-
// clicks on a .jml file. On macOS open file messages are handled in a different
// way; see ApplicationWindow.registerOpenFilesHandler().
//
// When the user double-clicks a .jml file, the OS opens a new instance of
// Juggling Lab. This class needs to use some form of inter-process communication
// to hand off the open file request to another Juggling Lab instance that may be
// already running.
//
// There are two implementations here: One that uses a memory-mapped file for
// IPC, and another that uses sockets. Both work well but the sockets version can
// trigger a Windows firewall warning that requires user approval, so we default
// to the other. Neither uses OS-specific APIs so they should be fairly portable.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions")

package org.jugglinglab.util

import java.io.File

object OpenFilesServer {
    // implemented types
    const val SERVER_MMF: Int = 1
    const val SERVER_SOCKETS: Int = 2

    // type selected for use in Juggling Lab
    const val OPEN_FILES_METHOD: Int = SERVER_MMF

    private var ofs_mmf: OpenFilesServerMmf? = null
    private var ofs_sockets: OpenFilesServerSockets? = null

    // Start up the OpenFilesServer thread, which listens for open file messages
    // from other instances of Juggling Lab.

    fun startOpenFilesServer() {
        when (OPEN_FILES_METHOD) {
            SERVER_MMF -> if (ofs_mmf == null) {
                ofs_mmf = OpenFilesServerMmf()
            }

            SERVER_SOCKETS -> if (ofs_sockets == null) {
                ofs_sockets = OpenFilesServerSockets()
            }
        }
    }

    // Try to signal another instance of Juggling Lab on this machine to open
    // the file. If the open command is successfully handed off, return true.
    // Otherwise return false.

    fun tryOpenFile(f: File?): Boolean {
        return when (OPEN_FILES_METHOD) {
            SERVER_MMF -> OpenFilesServerMmf.tryOpenFile(f)
            SERVER_SOCKETS -> OpenFilesServerSockets.tryOpenFile(f)
            else -> false
        }
    }

    // Do any needed cleanup when things are closing down.
    fun cleanup() {
        if (ofs_mmf != null) {
            OpenFilesServerMmf.cleanup()
            ofs_mmf = null
        }
        if (ofs_sockets != null) {
            OpenFilesServerSockets.cleanup()
            ofs_sockets = null
        }
    }
}
