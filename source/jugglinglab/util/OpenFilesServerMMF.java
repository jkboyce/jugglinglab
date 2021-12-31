// OpenFilesServerMMF.java
//
// Copyright 2002-2021 Jack Boyce and the Juggling Lab contributors

package jugglinglab.util;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import javax.swing.SwingUtilities;

import jugglinglab.core.ApplicationWindow;
import jugglinglab.core.Constants;


// This implementation of OpenFilesServer uses a memory mapped file to
// communicate between processes.

public class OpenFilesServerMMF extends Thread {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    static Thread server_thread;
    static String ipc_filename;
    static final int BUFFER_LENGTH = 1024;

    static {
        ipc_filename = System.getProperty("java.io.tmpdir") +
                       File.separator + "JugglingLabTemp"; 
    }


    public OpenFilesServerMMF() {
        if (server_thread != null)
            return;

        server_thread = this;
        start();
    }

    // Server thread loops forever, listening for messages
    @Override
    public void run() {
        if (Constants.DEBUG_OPEN_SERVER)
            System.out.println("server thread starting");

        File fipc = new File(ipc_filename);
        FileChannel chan = null;
        try {
            chan = FileChannel.open(fipc.toPath(), StandardOpenOption.READ,
                    StandardOpenOption.WRITE, StandardOpenOption.CREATE);

            MappedByteBuffer b_toserver = chan.map(MapMode.READ_WRITE, 0, BUFFER_LENGTH);
            CharBuffer buf_toserver = b_toserver.asCharBuffer();
            MappedByteBuffer b_fromserver = chan.map(MapMode.READ_WRITE, BUFFER_LENGTH, BUFFER_LENGTH);
            CharBuffer buf_fromserver = b_fromserver.asCharBuffer();

            String line;

            while (!interrupted()) {
                clearBuffer(buf_toserver);

                if (Constants.DEBUG_OPEN_SERVER)
                    System.out.println("Server waiting for message");
                while (!waitUntilMessage(buf_toserver, 1000))
                    ;

                line = readMessage(buf_toserver);
                if (Constants.DEBUG_OPEN_SERVER)
                    System.out.println("Got a message: " + line);

                if (line.startsWith("open ")) {
                    String filepath = line.substring(5, line.length());
                    File file = new File(filepath);

                    writeMessage(buf_fromserver, "opening " + filepath + '\0');

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
                    writeMessage(buf_fromserver, "Juggling Lab version " + Constants.version + '\0');
                } else if (line.startsWith("done")) {
                    writeMessage(buf_fromserver, "goodbye\0");
                } else
                    writeMessage(buf_fromserver, line + '\0');
            }
        } catch (IOException ioe) {
            ErrorDialog.handleFatalException(ioe);
        } catch (InterruptedException ie) {
            if (Constants.DEBUG_OPEN_SERVER)
                System.out.println("thread interrupted, deleting temp file");
        } finally {
            try {
                if (chan != null)
                    chan.close();
                Files.delete(fipc.toPath());
            } catch (Exception e) {
                if (Constants.DEBUG_OPEN_SERVER)
                    System.out.println(e);
            }
        }
    }

    public static boolean tryOpenFile(File f) {
        File fipc = new File(ipc_filename);

        if (!fipc.exists()) {
            if (Constants.DEBUG_OPEN_SERVER)
                System.out.println("temp file doesn't exist, cannot hand off");
            return false;
        }

        FileChannel chan = null;
        try {
            chan = FileChannel.open(fipc.toPath(), StandardOpenOption.READ,
                    StandardOpenOption.WRITE, StandardOpenOption.CREATE);

            MappedByteBuffer b_toserver = chan.map(MapMode.READ_WRITE, 0, BUFFER_LENGTH);
            CharBuffer buf_toserver = b_toserver.asCharBuffer();
            MappedByteBuffer b_fromserver = chan.map(MapMode.READ_WRITE, BUFFER_LENGTH, BUFFER_LENGTH);
            CharBuffer buf_fromserver = b_fromserver.asCharBuffer();

            String line;

            clearBuffer(buf_fromserver);
            writeMessage(buf_toserver, "identify\0");
            if (!waitUntilMessage(buf_fromserver, 200)) {
                if (Constants.DEBUG_OPEN_SERVER)
                    System.out.println("no ID response from server; exiting");
                return false;
            }
            line = readMessage(buf_fromserver);
            if (!line.equals("Juggling Lab version " + Constants.version)) {
                if (Constants.DEBUG_OPEN_SERVER) {
                    System.out.println("ID response didn't match: " + line);
                    System.out.println("exiting");
                }
                return false;
            }

            clearBuffer(buf_fromserver);
            writeMessage(buf_toserver, "open " + f + '\0');
            if (!waitUntilMessage(buf_fromserver, 500)) {
                if (Constants.DEBUG_OPEN_SERVER)
                    System.out.println("no open response from server; exiting");
                return false;
            }
            line = readMessage(buf_fromserver);
            if (!line.startsWith("opening ")) {
                if (Constants.DEBUG_OPEN_SERVER) {
                    System.out.println("Open response didn't match: " + line);
                    System.out.println("exiting");
                }
                return false;
            }

            writeMessage(buf_toserver, "done\0");
            return true;

        } catch (IOException ioe) {
            ErrorDialog.handleFatalException(ioe);
        } catch (InterruptedException ie) {
            ErrorDialog.handleFatalException(ie);
        } 

        return false;
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

    // convenience methods to handle messaging through our memory mapped file

    private static void clearBuffer(CharBuffer cb) {
        cb.put(0, '\0');
    }

    private static String readMessage(CharBuffer cb) {
        StringBuffer msg_sb = new StringBuffer(BUFFER_LENGTH);
        for (int i = 0; i < BUFFER_LENGTH; ++i) {
            char c = cb.get(i);
            if (c == '\0')
                break;
            msg_sb.append(c);
        }
        return msg_sb.toString();
    }

    private static void writeMessage(CharBuffer cb, String str) {
        for (int i = 1; i < str.length(); ++i)
            cb.put(i, str.charAt(i));
        cb.put(0, str.charAt(0));
    }

    private static boolean waitUntilMessage(CharBuffer cb, int timeout) throws InterruptedException {
        int tries = timeout / 20;

        for (int i = 0; i < tries; ++i) {
            if (cb.get(0) != '\0')
                return true;
            Thread.sleep(20);
        }
        return false;
    }
}
