// OpenFilesServerSockets.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.util;

import java.awt.Desktop;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import javax.swing.SwingUtilities;

import jugglinglab.core.ApplicationWindow;
import jugglinglab.core.Constants;


// This implementation of OpenFilesServer uses sockets on a localhost loopback
// connection to communicate between processes.

public class OpenFilesServerSockets extends Thread {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    static Thread server_thread;
    static protected final int OPEN_FILES_PORT = 8686;
    static protected final int POLLING_TIMEOUT_MS = 30;

    protected ServerSocket listen_socket;


    public OpenFilesServerSockets() {
        if (server_thread != null)
            return;

        try {
            listen_socket = new ServerSocket(OPEN_FILES_PORT);
            listen_socket.setSoTimeout(POLLING_TIMEOUT_MS);
        } catch (IOException e) {
            if (Constants.DEBUG_OPEN_SERVER)
                System.out.println("Server already running on machine; thread is not starting");
            // ErrorDialog.handleFatalException(e);
            return;
        }
        server_thread = this;
        start();
    }

    // Server thread loops forever, listening for connections on our port
    @Override
    public void run() {
        if (Constants.DEBUG_OPEN_SERVER)
            System.out.println("Server: listening on port " + OPEN_FILES_PORT);

        try {
            while (!interrupted()) {
                try {
                    Socket client_socket = listen_socket.accept();

                    if (Constants.DEBUG_OPEN_SERVER) {
                        System.out.println("Server got a connection from " +
                                client_socket.getInetAddress() + ":" +
                                client_socket.getPort());
                    }

                    if (client_socket.getInetAddress().toString().contains("127.0.0.1"))
                        new Connection(client_socket);
                    else if (Constants.DEBUG_OPEN_SERVER)
                        System.out.println("Ignoring connection request; not from 127.0.0.1");
                } catch (SocketTimeoutException ste) {
                }
            }
        } catch (IOException e) {
            ErrorDialog.handleFatalException(e);
        } finally {
            try {
                listen_socket.close();
            } catch (IOException ioe) {}
            listen_socket = null;
        }
    }

    static public boolean tryOpenFile(File f) {
        Socket s = null;
        BufferedReader sin = null;
        PrintStream sout = null;

        try {
            s = new Socket("localhost", OPEN_FILES_PORT);
            sin = new BufferedReader(new InputStreamReader(s.getInputStream()));
            sout = new PrintStream(s.getOutputStream());

            if (Constants.DEBUG_OPEN_SERVER) {
                System.out.println("Connected to " + s.getInetAddress()
                           + ":"+ s.getPort());
            }

            String line;

            sout.println("identify");
            line = sin.readLine();
            if (!line.equals("Juggling Lab version " + Constants.version)) {
                if (Constants.DEBUG_OPEN_SERVER) {
                    System.out.println("ID response didn't match: " + line);
                    System.out.println("exiting");
                }
                return false;
            }

            sout.println("open " + f);
            line = sin.readLine();
            if (!line.startsWith("opening ")) {
                if (Constants.DEBUG_OPEN_SERVER) {
                    System.out.println("Open response didn't match: " + line);
                    System.out.println("exiting");
                }
                return false;
            }

            sout.println("done");
            line = sin.readLine();

            return true;

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
        } catch (IOException e) {
            if (Constants.DEBUG_OPEN_SERVER)
                System.out.println("No server detected on port " + OPEN_FILES_PORT);
            return false;
        } finally {
            try {
                if (s != null) s.close();
            } catch (IOException e2) {}
        }
    }

    public static void cleanup() {
        if (server_thread != null) {
            try {
                server_thread.interrupt();
                server_thread.join(100);
            } catch (InterruptedException ie) {
            }
            server_thread = null;
        }
    }
}


class Connection extends Thread {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected Socket client;
    protected BufferedReader in;
    protected PrintStream out;


    public Connection(Socket client_socket) {
        client = client_socket;
        try {
            in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            out = new PrintStream(client.getOutputStream());
        } catch (IOException ioe) {
            try {
                client.close();
            } catch (IOException ioe2) {}

            ErrorDialog.handleFatalException(ioe);
            return;
        }
        start();
    }

    @Override
    public void run() {
        String line;

        if (Constants.DEBUG_OPEN_SERVER)
            System.out.println("Server started connection thread");

        try {
            while (true) {
                line = in.readLine();
                if (line == null)
                    return;

                if (line.startsWith("open ")) {
                    String filepath = line.substring(5, line.length());
                    File file = new File(filepath);

                    out.println("opening " + filepath);

                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            if (Desktop.isDesktopSupported()
                                    && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND))
                                Desktop.getDesktop().requestForeground(true);

                            try {
                                ApplicationWindow.openJMLFile(file);
                            } catch (JuggleExceptionUser jeu) {
                                String template = errorstrings.getString("Error_reading_file");
                                Object[] arguments = { file.getName() };
                                String msg = MessageFormat.format(template, arguments) +
                                             ":\n" + jeu.getMessage();
                                new ErrorDialog(null, msg);
                            } catch (JuggleExceptionInternal jei) {
                                ErrorDialog.handleFatalException(jei);
                            }
                        }
                    });
                } else if (line.startsWith("identify")) {
                    out.println("Juggling Lab version " + Constants.version);
                } else if (line.startsWith("done")) {
                    out.println("goodbye");
                    return;
                } else
                    out.println(line);
            }
        } catch (IOException ioe) {
        } finally {
            try {
                client.close();
            } catch (IOException ioe2) {}
        }
    }
}
