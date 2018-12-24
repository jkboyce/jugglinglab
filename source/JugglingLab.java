// JugglingLab.java
//
// Copyright 2018 by Jack Boyce (jboyce@gmail.com) and others

/*
    This file is part of Juggling Lab.

    Juggling Lab is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    Juggling Lab is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Juggling Lab; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

import java.applet.Applet;
import java.applet.AudioClip;
import java.io.*;
import java.net.URL;

import jugglinglab.core.*;
import jugglinglab.util.*;


public class JugglingLab {

    public static void loadMediaResources() {
        // Load sound and graphics resources for later use

        AudioClip[] clips = new AudioClip[2];
        URL catchurl = JugglingLab.class.getResource("/resources/catch.au");
        if (catchurl != null)
            clips[0] = Applet.newAudioClip(catchurl);
        URL bounceurl = JugglingLab.class.getResource("/resources/bounce.au");
        if (bounceurl != null)
            clips[1] = Applet.newAudioClip(bounceurl);
        Animator.setAudioClips(clips);

        // The class loader delegation model makes it difficult to find resources from
        // within the VersionSpecific class.  There must be a better way to do it.
        URL[] images = new URL[2];
        images[0] = JugglingLab.class.getResource("/resources/ball.gif");
        images[1] = JugglingLab.class.getResource("/resources/ball.png");
        VersionSpecific.setDefaultPropImages(images);
    }

    // main entry point

    public static void main(String[] args) {
        // do some os-specific setup
        String osname = System.getProperty("os.name").toLowerCase();
        boolean isMacOS = osname.startsWith("mac os x");
        if (isMacOS) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }

        try {
            JugglingLab.loadMediaResources();
            new ApplicationWindow("Juggling Lab");
        } catch (JuggleExceptionUser jeu) {
            new ErrorDialog(null, jeu.getMessage());
        } catch (JuggleExceptionInternal jei) {
            ErrorDialog.handleException(jei);
        }
    }
}
