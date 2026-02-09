//
// OpenFilesServerSockets.kt
//
// This implementation of OpenFilesServer uses sockets on a localhost loopback
// connection to communicate between processes.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions")

package org.jugglinglab.util

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.ui.ApplicationWindow
import java.awt.Desktop
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import javax.swing.SwingUtilities

class OpenFilesServerSockets : Thread() {
    private var listenSocket: ServerSocket? = null

    init {
        if (serverThread == null) {
            try {
                listenSocket = ServerSocket(OPEN_FILES_PORT)
                listenSocket!!.setSoTimeout(POLLING_TIMEOUT_MS)
                serverThread = this
                start()
            } catch (_: IOException) {
                if (org.jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                    println("Server already running on machine; thread is not starting")
                }
            }
        }
    }

    // Server thread loops forever, listening for connections on our port.
    override fun run() {
        if (org.jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
            println("Server: listening on port $OPEN_FILES_PORT")
        }

        try {
            while (!interrupted()) {
                try {
                    val clientSocket = listenSocket!!.accept()

                    if (org.jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                        println(
                            ("Server got a connection from "
                                    + clientSocket.getInetAddress()
                                    + ":"
                                    + clientSocket.getPort())
                        )
                    }

                    if (clientSocket.getInetAddress().toString().contains("127.0.0.1")) {
                        Connection(clientSocket)
                    } else if (org.jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                        println("Ignoring connection request; not from 127.0.0.1")
                    }
                } catch (_: SocketTimeoutException) {
                }
            }
        } catch (e: IOException) {
            jlHandleFatalException(e)
        } finally {
            try {
                listenSocket!!.close()
            } catch (_: IOException) {
            }
            listenSocket = null
        }
    }

    companion object {
        var serverThread: Thread? = null
        private const val OPEN_FILES_PORT: Int = 8686
        private const val POLLING_TIMEOUT_MS: Int = 30

        fun tryOpenFile(f: File?): Boolean {
            var s: Socket? = null
            val sin: BufferedReader?
            val sout: PrintStream?

            try {
                s = Socket("localhost", OPEN_FILES_PORT)
                sin = BufferedReader(InputStreamReader(s.getInputStream()))
                sout = PrintStream(s.getOutputStream())

                if (org.jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                    println("Connected to " + s.getInetAddress() + ":" + s.getPort())
                }

                sout.println("identify")
                var line: String = sin.readLine()
                if (line != "Juggling Lab version " + org.jugglinglab.core.Constants.VERSION) {
                    if (org.jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                        println("ID response didn't match: $line")
                        println("exiting")
                    }
                    return false
                }

                sout.println("open $f")
                line = sin.readLine()
                if (!line.startsWith("opening ")) {
                    if (org.jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                        println("Open response didn't match: $line")
                        println("exiting")
                    }
                    return false
                }

                sout.println("done")
                sin.readLine()

                return true

                /*
      // interactive console app to talk with the server

      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      while (true) {
          System.out.print("> ");
          System.out.flush();

          line = in.readLine();
          if (line == null)
              break;

          sout.println(line);
          line = sin.readLine();
          if (line == null) {
              System.out.println("Connection closed by server.");
              break;
          }
          System.out.println(line);
      }
      return true;
      */
            } catch (_: IOException) {
                if (org.jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
                    println("No server detected on port $OPEN_FILES_PORT")
                }
                return false
            } finally {
                try {
                    s?.close()
                } catch (_: IOException) {
                }
            }
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
    }
}

internal class Connection(var client: Socket) : Thread() {
    private var inReader: BufferedReader? = null
    private var outStream: PrintStream? = null

    init {
        try {
            inReader = BufferedReader(InputStreamReader(client.getInputStream()))
            outStream = PrintStream(client.getOutputStream())
            start()
        } catch (ioe: IOException) {
            try {
                client.close()
            } catch (_: IOException) {
            }
            jlHandleFatalException(ioe)
        }
    }

    override fun run() {
        if (org.jugglinglab.core.Constants.DEBUG_OPEN_SERVER) {
            println("Server started connection thread")
        }

        try {
            while (true) {
                val line = inReader!!.readLine() ?: return

                if (line.startsWith("open ")) {
                    val filepath = line.substring(5)
                    val file = File(filepath)
                    outStream!!.println("opening $filepath")

                    SwingUtilities.invokeLater {
                        if (Desktop.isDesktopSupported()
                            && Desktop.getDesktop()
                                .isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)
                        ) {
                            Desktop.getDesktop().requestForeground(true)
                        }
                        try {
                            ApplicationWindow.Companion.openJmlFile(file)
                        } catch (jeu: JuggleExceptionUser) {
                            val message = jlGetStringResource(Res.string.error_reading_file, file.getName())
                            val msg = message + ":\n" + jeu.message
                            jlHandleUserException(null, msg)
                        } catch (jei: JuggleExceptionInternal) {
                            jlHandleFatalException(jei)
                        }
                    }
                } else if (line.startsWith("identify")) {
                    outStream!!.println("Juggling Lab version " + org.jugglinglab.core.Constants.VERSION)
                } else if (line.startsWith("done")) {
                    outStream!!.println("goodbye")
                    return
                } else {
                    outStream!!.println(line)
                }
            }
        } catch (_: IOException) {
        } finally {
            try {
                client.close()
            } catch (_: IOException) {
            }
        }
    }
}
