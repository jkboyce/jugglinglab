// UpdateChecker.java
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

package jugglinglab.util;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.MalformedURLException;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.*;

import jugglinglab.core.Constants;


public class UpdateChecker extends Thread {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;

    protected Component parent;


    public UpdateChecker(Component parent) {
        this.parent = parent;
        this.setPriority(Thread.MIN_PRIORITY);
        this.start();
    }

    @Override
    public void run() {
        // download web page at https://jugglinglab.org
        // parse for the most recent version number
        // if newer than this one, pop up a dialog asking if they'd like to download an update
        // if user clicks yes, open a browser window to https://jugglinglab.org

        InputStream is = null;
        InputStreamReader isr = null;
        String line = null;

        try {
            URL url = new URL(Constants.site_URL);
            is = url.openStream();  // throws an IOException
            isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);

            while ((line = br.readLine()) != null) {
                if (line.contains("versionstring"))
                    break;
            }
        } catch (MalformedURLException mue) {
            // handle errors quietly; no big deal if this background operation fails
        } catch (IOException ioe) {
        } finally {
            try {
                if (isr != null) isr.close();
                if (is != null) is.close();
            } catch (IOException ioe) {
            }
        }

        // something went wrong, fail quietly
        if (line == null)
            return;

        // regular expression looks for a span tag with id "versionstring"
        // surrounding the version number string
        String pattern = ".*versionstring.*?>(.*?)<.*";
        final String current_version = line.replaceAll(pattern, "$1");
        String running_version = Constants.version;

        if (current_version == null || current_version.length() == 0 ||
                    compareVersions(current_version, running_version) <= 0)
            return;

        try {
            Thread.sleep(3000);
        } catch (Exception e) {}

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                showUpdateBox(current_version);
            }
        });
    }

    // returns 0 if equal, less than 0 if v1 < v2, greater than 0 if v1 > v2
    protected static int compareVersions(String v1, String v2) {
        String[] components1 = v1.split("\\.");
        String[] components2 = v2.split("\\.");
        int length = Math.min(components1.length, components2.length);
        for (int i = 0; i < length; i++) {
            int result = new Integer(components1[i]).compareTo(Integer.parseInt(components2[i]));
            if (result != 0)
                return result;
        }
        return Integer.compare(components1.length, components2.length);
    }

    protected static void showUpdateBox(String version) {
        final String title = guistrings.getString("New_version_available");
        final JFrame updateBox = new JFrame(title);
        updateBox.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel updatePanel = new JPanel();
        GridBagLayout gb = new GridBagLayout();
        updatePanel.setLayout(gb);

        String template1 = guistrings.getString("New_version_text1");
        Object[] arguments1 = { version };
        JLabel text1 = new JLabel(MessageFormat.format(template1, arguments1));
        text1.setFont(new Font("SansSerif", Font.PLAIN, 14));
        updatePanel.add(text1);
        gb.setConstraints(text1, make_constraints(GridBagConstraints.LINE_START,0,1,
                                                       new Insets(20,25,0,25)));

        String template2 = guistrings.getString("New_version_text2");
        Object[] arguments2 = { Constants.version };
        JLabel text2 = new JLabel(MessageFormat.format(template2, arguments2));
        text2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        updatePanel.add(text2);
        gb.setConstraints(text2, make_constraints(GridBagConstraints.LINE_START,0,2,
                                                       new Insets(0,25,0,25)));

        JLabel text3 = new JLabel(guistrings.getString("New_version_text3"));
        text3.setFont(new Font("SansSerif", Font.PLAIN, 14));
        updatePanel.add(text3);
        gb.setConstraints(text3, make_constraints(GridBagConstraints.LINE_START,0,3,
                                                       new Insets(20,25,5,25)));

        JPanel butp = new JPanel();
        butp.setLayout(new FlowLayout(FlowLayout.LEADING));
        JButton cancelbutton = new JButton(guistrings.getString("Update_cancel"));
        cancelbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateBox.dispose();
            }
        });
        butp.add(cancelbutton);

        JButton yesbutton = new JButton(guistrings.getString("Update_yes"));
        yesbutton.setDefaultCapable(true);
        yesbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean browse_supported = (Desktop.isDesktopSupported() &&
                                Desktop.getDesktop().isSupported(Desktop.Action.BROWSE));
                boolean browse_problem = false;

                if (browse_supported) {
                    try {
                        Desktop.getDesktop().browse(new URI(Constants.download_URL));
                    } catch (Exception excep) {
                        browse_problem = true;
                    }
                }

                if (!browse_supported || browse_problem) {
                    String template3 = guistrings.getString("Download_message");
                    Object[] arguments3 = { Constants.download_URL };
                    String message = MessageFormat.format(template3, arguments3);
                    new LabelDialog(updateBox, title, message);
                }
                updateBox.dispose();
            }
        });
        butp.add(yesbutton);

        updatePanel.add(butp);
        gb.setConstraints(butp, make_constraints(GridBagConstraints.LINE_END,0,4,
                                                 new Insets(10,10,10,10)));

        updatePanel.setOpaque(true);
        updateBox.setContentPane(updatePanel);
        updateBox.getRootPane().setDefaultButton(yesbutton);

        Locale loc = JLLocale.getLocale();
        updateBox.applyComponentOrientation(ComponentOrientation.getOrientation(loc));

        updateBox.pack();
        updateBox.setResizable(false);
        updateBox.setLocationRelativeTo(null);    // center frame on screen
        updateBox.setVisible(true);
    }

    protected static GridBagConstraints make_constraints(int location, int gridx, int gridy, Insets ins) {
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.anchor = location;
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridheight = gbc.gridwidth = 1;
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbc.insets = ins;
        gbc.weightx = gbc.weighty = 0.0;
        return gbc;
    }
}
