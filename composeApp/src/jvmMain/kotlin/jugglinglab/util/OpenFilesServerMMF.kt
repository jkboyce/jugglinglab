//
// OpenFilesServerMMF.kt
//
// This implementation of OpenFilesServer uses a memory mapped file to
// communicate between processes.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions")

package jugglinglab.util

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.ApplicationWindow
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.nio.CharBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import javax.swing.SwingUtilities

class OpenFilesServerMMF : Thread() {
    init {
        serverThread = this
        start()
    }

    // Run main server thread, which listens for messages.
    override fun run() {
        if (jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
            println("server thread starting")
        }

        val fipc = File(ipcFilename)
        var chan: FileChannel? = null
        try {
            chan = FileChannel.open(
                fipc.toPath(),
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE
            )

            val bToserver = chan.map(MapMode.READ_WRITE, 0, BUFFER_LENGTH.toLong())
            val bufToserver = bToserver.asCharBuffer()
            val bFromserver = chan.map(MapMode.READ_WRITE, BUFFER_LENGTH.toLong(), BUFFER_LENGTH.toLong())
            val bufFromserver = bFromserver.asCharBuffer()

            var line: String?

            while (!interrupted()) {
                clearBuffer(bufToserver)

                if (jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                    println("Server waiting for message")
                }
                @Suppress("ControlFlowWithEmptyBody")
                while (!waitUntilMessage(bufToserver, 1000)) {
                }

                line = readMessage(bufToserver)
                if (jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                    println("Got a message: $line")
                }

                if (line.startsWith("open ")) {
                    val filepath = line.substring(5)
                    val file = File(filepath)

                    writeMessage(bufFromserver, "opening $filepath\u0000")

                    SwingUtilities.invokeLater {
                        if (Desktop.isDesktopSupported()
                            && Desktop.getDesktop()
                                .isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)
                        ) {
                            Desktop.getDesktop().requestForeground(true)
                        }
                        try {
                            ApplicationWindow.openJMLFile(file)
                        } catch (jeu: JuggleExceptionUser) {
                            val message = jlGetStringResource(Res.string.error_reading_file, file.getName())
                            val msg = message + ":\n" + jeu.message
                            jlHandleUserException(null, msg)
                        } catch (jei: JuggleExceptionInternal) {
                            jlHandleFatalException(jei)
                        }
                    }
                } else if (line.startsWith("identify")) {
                    writeMessage(
                        bufFromserver,
                        "Juggling Lab version " + jugglinglab.core.Constants.VERSION + '\u0000'
                    )
                } else if (line.startsWith("done")) {
                    writeMessage(bufFromserver, "goodbye\u0000")
                } else {
                    writeMessage(bufFromserver, line + '\u0000')
                }
            }
        } catch (ioe: IOException) {
            jlHandleFatalException(ioe)
        } catch (_: InterruptedException) {
            if (jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                println("thread interrupted, deleting temp file")
            }
        } finally {
            try {
                chan?.close()
                Files.delete(fipc.toPath())
            } catch (e: Exception) {
                if (jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                    println(e)
                }
            }
        }
    }

    companion object {
        var serverThread: Thread? = null
        var ipcFilename: String = System.getProperty("java.io.tmpdir") + File.separator +
            "JugglingLabTemp"
        const val BUFFER_LENGTH: Int = 1024

        // Try to open file `f` by messaging another running instance of Juggling
        // Lab.
        //
        // Returns true if handed off successfully, false otherwise.

        fun tryOpenFile(f: File?): Boolean {
            val fipc = File(ipcFilename)
            if (!fipc.exists()) {
                if (jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                    println("temp file doesn't exist, cannot hand off")
                }
                return false
            }

            try {
                val chan = FileChannel.open(
                    fipc.toPath(),
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE
                )

                val bToserver = chan.map(MapMode.READ_WRITE, 0, BUFFER_LENGTH.toLong())
                val bufToserver = bToserver.asCharBuffer()
                val bFromserver = chan.map(MapMode.READ_WRITE, BUFFER_LENGTH.toLong(),
                    BUFFER_LENGTH.toLong())
                val bufFromserver = bFromserver.asCharBuffer()

                clearBuffer(bufFromserver)
                writeMessage(bufToserver, "identify\u0000")
                if (!waitUntilMessage(bufFromserver, 200)) {
                    if (jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                        println("no ID response from server; exiting")
                    }
                    return false
                }
                var line = readMessage(bufFromserver)
                if (line != "Juggling Lab version " + jugglinglab.core.Constants.VERSION) {
                    if (jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                        println("ID response didn't match: $line")
                        println("exiting")
                    }
                    return false
                }

                clearBuffer(bufFromserver)
                writeMessage(bufToserver, "open $f\u0000")
                if (!waitUntilMessage(bufFromserver, 500)) {
                    if (jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                        println("no open response from server; exiting")
                    }
                    return false
                }
                line = readMessage(bufFromserver)
                if (!line.startsWith("opening ")) {
                    if (jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                        println("Open response didn't match: $line")
                        println("exiting")
                    }
                    return false
                }

                writeMessage(bufToserver, "done\u0000")
                return true
            } catch (ioe: IOException) {
                jlHandleFatalException(ioe)
            } catch (ioe: InterruptedException) {
                jlHandleFatalException(ioe)
            }

            return false
        }

        fun cleanup() {
            if (serverThread != null) {
                try {
                    serverThread!!.interrupt()
                    serverThread!!.join(100)
                } catch (_: InterruptedException) {
                }
                serverThread = null
            }
        }

        //----------------------------------------------------------------------
        // Methods to handle messaging through our memory mapped file
        //----------------------------------------------------------------------

        private fun clearBuffer(cb: CharBuffer) {
            cb.put(0, '\u0000')
        }

        private fun readMessage(cb: CharBuffer): String {
            val msgSb = StringBuilder(BUFFER_LENGTH)
            for (i in 0..<BUFFER_LENGTH) {
                val c = cb.get(i)
                if (c == '\u0000') {
                    break
                }
                msgSb.append(c)
            }
            return msgSb.toString()
        }

        private fun writeMessage(cb: CharBuffer, str: String) {
            for (i in 1..<str.length) {
                cb.put(i, str[i])
            }
            cb.put(0, str[0])
        }

        // Wait until a message arrives into buffer `cb`, for a maximum of
        // `timeout` milliseconds.
        //
        // Returns true if a message was read, false otherwise.

        @Throws(InterruptedException::class)
        private fun waitUntilMessage(cb: CharBuffer, timeout: Int): Boolean {
            val tries = timeout / 20
            repeat (tries) {
                if (cb.get(0) != '\u0000') {
                    return true
                }
                sleep(20)
            }
            return false
        }
    }
}
