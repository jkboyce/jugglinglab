// OpenFilesServer.java
//
// Copyright 2002-2021 Jack Boyce and the Juggling Lab contributors

package jugglinglab.util;

import java.io.File;

import jugglinglab.core.Constants;


// This class is used to handle open file messages on Windows, i.e., when the user
// double-clicks on a .jml file.
//
// The OS opens a new instance of Juggling Lab, and this class needs to provide
// some kind of inter-process communication to hand off the open file request to
// another Juggling Lab instance that may be already running.

public class OpenFilesServer {
    public static final int memorymappedfile = 1;    // implemented types
    public static final int sockets = 2;

    private static OpenFilesServerMMF ofs_mmf;
    private static OpenFilesServerMMF ofs_sockets;


    public OpenFilesServer() {
        switch (Constants.OPEN_FILES_METHOD) {
            case memorymappedfile:
                ofs_mmf = new OpenFilesServerMMF();
                break;
            case sockets:
                ofs_sockets = new OpenFilesServerMMF();
                break;
        }
    }

    // Try to signal another instance of Juggling Lab on this machine to open
    // the file. If the open command is successfully handed off, return true.
    // Otherwise return false.
    public static boolean tryOpenFile(File f) {
        switch (Constants.OPEN_FILES_METHOD) {
            case memorymappedfile:
                return OpenFilesServerMMF.tryOpenFile(f);
            case sockets:
                return OpenFilesServerMMF.tryOpenFile(f);
        }
        return false;
    }

    // Do any needed cleanup when things are closing down
    public static void cleanup() {
        if (ofs_mmf != null)
            ofs_mmf.cleanup();
        if (ofs_sockets != null)
            ofs_sockets.cleanup();
    }
}
