// OpenFilesServer.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.util;

import java.io.File;

import jugglinglab.core.Constants;


// This class is used to handle open file messages, i.e., when the user double-
// clicks on a .jml file. This is primarily used on Windows; on macOS the open
// file messages are handled through the Desktop class.
//
// The OS opens a new instance of Juggling Lab, and this class needs to use
// some kind of inter-process communication to hand off the open file request
// to another Juggling Lab instance that may be already running.
//
// There are two distinct implementations here: One that uses a memory-mapped
// file for IPC, and another that uses sockets. Both work well but the sockets
// version can trigger a Windows firewall warning that requires user approval,
// so we default to the other. Neither uses OS-specific APIs so they should be
// fairly portable.

public class OpenFilesServer {
    // implemented types
    public static final int SERVER_MMF = 1;
    public static final int SERVER_SOCKETS = 2;

    private static OpenFilesServerMMF ofs_mmf;
    private static OpenFilesServerSockets ofs_sockets;


    public OpenFilesServer() {
        switch (Constants.OPEN_FILES_METHOD) {
            case SERVER_MMF:
                if (ofs_mmf == null)
                    ofs_mmf = new OpenFilesServerMMF();
                break;
            case SERVER_SOCKETS:
                if (ofs_sockets == null)
                    ofs_sockets = new OpenFilesServerSockets();
                break;
        }
    }

    // Try to signal another instance of Juggling Lab on this machine to open
    // the file. If the open command is successfully handed off, return true.
    // Otherwise return false.
    public static boolean tryOpenFile(File f) {
        switch (Constants.OPEN_FILES_METHOD) {
            case SERVER_MMF:
                return OpenFilesServerMMF.tryOpenFile(f);
            case SERVER_SOCKETS:
                return OpenFilesServerSockets.tryOpenFile(f);
        }
        return false;
    }

    // Do any needed cleanup when things are closing down
    public static void cleanup() {
        if (ofs_mmf != null) {
            OpenFilesServerMMF.cleanup();
            ofs_mmf = null;
        }
        if (ofs_sockets != null) {
            OpenFilesServerSockets.cleanup();
            ofs_sockets = null;
        }
    }
}
